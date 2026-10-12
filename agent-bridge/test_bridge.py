#!/usr/bin/env python3
"""Integration tests for bridge.py using a fake `claude` executable. Run: python3 test_bridge.py"""
import json
import os
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bridge  # noqa: E402

TOKEN = "test-token-1234567890"

FAKE_CLAUDE = r'''#!{py}
import json, os, sys, time
args = sys.argv[1:]
if "--version" in args:
    print("9.9.9 (Fake Claude)"); sys.exit(0)
prompt = sys.stdin.read()
mode = args[args.index("--permission-mode") + 1]
tools = args[args.index("--allowedTools") + 1]
def emit(o): print(json.dumps(o), flush=True)
emit({{"type": "system", "subtype": "init", "model": "fake"}})
if "SLEEP" in prompt:
    time.sleep(30)
if "FAIL" in prompt:
    print("something broke, api_key=sk-SECRETSECRET123", file=sys.stderr); sys.exit(3)
emit({{"type": "assistant", "message": {{"content": [
    {{"type": "text", "text": "mode=%s tools=%s" % (mode, tools)}},
    {{"type": "tool_use", "name": "Read", "input": {{"file_path": "a.txt"}}}}]}}}})
if mode == "acceptEdits":
    with open("edited.txt", "w") as f: f.write("hello\n")
    with open("a.txt", "a") as f: f.write("changed\n")
emit({{"type": "result", "subtype": "success", "is_error": False,
      "result": "RESULT mode=%s Bearer abc.def.ghi" % mode}})
'''


class BridgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        root = cls.tmp.name
        cls.proj = os.path.join(root, "proj")
        os.mkdir(cls.proj)
        cls.outside = os.path.join(root, "outside")
        os.mkdir(cls.outside)
        run = lambda *a: subprocess.run(a, cwd=cls.proj, check=True, capture_output=True)
        run("git", "init", "-q")
        run("git", "config", "user.email", "t@example.com")
        run("git", "config", "user.name", "t")
        with open(os.path.join(cls.proj, "a.txt"), "w") as f:
            f.write("original\n")
        run("git", "add", ".")
        run("git", "commit", "-qm", "init")

        fake = os.path.join(root, "fake-claude")
        with open(fake, "w") as f:
            f.write(FAKE_CLAUDE.format(py=sys.executable))
        os.chmod(fake, 0o755)

        config = {
            "claude_bin": fake,
            "task_timeout_seconds": 3,
            "command_timeout_seconds": 3,
            "workspaces": {"proj": cls.proj, "missing": os.path.join(root, "nope")},
            "commands": [
                {"id": "echo", "label": "Echo", "argv": ["echo", "hello from command"]},
                {"id": "fail", "label": "Fail", "argv": ["sh", "-c", "echo bad; exit 7"]},
                {"id": "slow", "label": "Slow", "argv": ["sleep", "20"]},
                {"id": "broken", "argv": "not-a-list"},
            ],
        }
        cls.bridge = bridge.Bridge(config, TOKEN)
        bridge.Handler.bridge = cls.bridge
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), bridge.Handler)
        cls.port = cls.server.server_address[1]
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.tmp.cleanup()

    # -- helpers
    def call(self, method, path, body=None, token=TOKEN):
        req = urllib.request.Request("http://127.0.0.1:%d%s" % (self.port, path), method=method,
                                     data=json.dumps(body).encode() if body is not None else None)
        if token is not None:
            req.add_header("Authorization", "Bearer " + token)
        req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read() or b"{}")

    def wait(self, task_id, timeout=10):
        end = time.time() + timeout
        while time.time() < end:
            _, snap = self.call("GET", "/tasks/%s?since=0" % task_id)
            if snap["status"] != "running":
                return snap
            time.sleep(0.1)
        self.fail("task did not finish")

    def reset_project(self):
        subprocess.run(["git", "checkout", "--", "."], cwd=self.proj, check=True, capture_output=True)
        subprocess.run(["git", "clean", "-fdq"], cwd=self.proj, check=True, capture_output=True)

    # -- auth
    def test_requires_token(self):
        self.assertEqual(self.call("GET", "/health", token=None)[0], 401)
        self.assertEqual(self.call("GET", "/health", token="wrong")[0], 401)
        self.assertEqual(self.call("POST", "/tasks", {}, token="wrong")[0], 401)
        code, body = self.call("GET", "/health")
        self.assertEqual(code, 200)
        self.assertIn("Fake Claude", body["claude"])

    # -- configuration / allowlists
    def test_only_valid_workspaces_and_commands_are_exposed(self):
        self.assertEqual(self.call("GET", "/workspaces")[1]["workspaces"], ["proj"])
        ids = [c["id"] for c in self.call("GET", "/commands")[1]["commands"]]
        self.assertEqual(sorted(ids), ["echo", "fail", "slow"])

    def test_rejects_paths_unknown_workspaces_and_bad_input(self):
        for ws in (self.outside, "../outside", "missing", "", None, 5):
            code, _ = self.call("POST", "/tasks", {"workspace": ws, "prompt": "x", "mode": "plan"})
            self.assertEqual(code, 404, ws)
        self.assertEqual(self.call("POST", "/tasks", {"workspace": "proj", "prompt": "", "mode": "plan"})[0], 400)
        self.assertEqual(self.call("POST", "/tasks", {"workspace": "proj", "prompt": "x", "mode": "root"})[0], 400)
        self.assertEqual(self.call("POST", "/tasks", {"workspace": "proj", "prompt": "x" * 30000, "mode": "plan"})[0], 400)
        self.assertEqual(self.call("GET", "/diff?workspace=" + self.outside)[0], 404)

    def test_arbitrary_commands_are_refused(self):
        code, _ = self.call("POST", "/run", {"workspace": "proj", "command_id": "rm -rf /"})
        self.assertEqual(code, 404)
        code, _ = self.call("POST", "/run", {"workspace": "proj", "command_id": "broken"})
        self.assertEqual(code, 404)

    # -- plan / edit flow
    def test_plan_is_read_only_and_returns_redacted_result(self):
        code, body = self.call("POST", "/tasks", {"workspace": "proj", "prompt": "plan it", "mode": "plan"})
        self.assertEqual(code, 200)
        snap = self.wait(body["id"])
        self.assertEqual(snap["status"], "done")
        text = "\n".join(l["text"] for l in snap["lines"])
        self.assertIn("mode=plan tools=Read,Glob,Grep", text)
        self.assertIn("Read a.txt", text)
        self.assertNotIn("abc.def.ghi", snap["result"])
        self.assertIn("RESULT mode=plan", snap["result"])
        self.assertFalse(os.path.exists(os.path.join(self.proj, "edited.txt")))

    def test_edit_flow_changes_files_and_diff_shows_them(self):
        self.reset_project()
        code, body = self.call("POST", "/tasks", {"workspace": "proj", "prompt": "edit it", "mode": "edit"})
        self.assertEqual(code, 200)
        snap = self.wait(body["id"])
        self.assertEqual(snap["status"], "done")
        text = "\n".join(l["text"] for l in snap["lines"])
        self.assertIn("mode=acceptEdits tools=Read,Glob,Grep,Edit,Write", text)
        self.assertNotIn("Bash", text.split("tools=")[1].split("\n")[0])
        code, d = self.call("GET", "/diff?workspace=proj")
        self.assertEqual(code, 200)
        self.assertIn("+changed", d["diff"])
        self.assertIn("edited.txt", d["status"])
        self.reset_project()

    def test_failure_is_reported_and_secrets_are_redacted(self):
        _, body = self.call("POST", "/tasks", {"workspace": "proj", "prompt": "FAIL", "mode": "plan"})
        snap = self.wait(body["id"])
        self.assertEqual(snap["status"], "failed")
        self.assertEqual(snap["exit_code"], 3)
        text = "\n".join(l["text"] for l in snap["lines"])
        self.assertNotIn("SECRETSECRET123", text)
        self.assertIn("[REDACTED]", text)

    def test_cancel_and_single_task_per_project(self):
        _, body = self.call("POST", "/tasks", {"workspace": "proj", "prompt": "SLEEP", "mode": "plan"})
        tid = body["id"]
        time.sleep(0.5)
        self.assertEqual(self.call("POST", "/tasks", {"workspace": "proj", "prompt": "y", "mode": "plan"})[0], 409)
        self.assertEqual(self.call("POST", "/tasks/%s/cancel" % tid, {})[0], 200)
        snap = self.wait(tid)
        self.assertEqual(snap["status"], "cancelled")

    def test_task_timeout(self):
        _, body = self.call("POST", "/tasks", {"workspace": "proj", "prompt": "SLEEP", "mode": "plan"})
        snap = self.wait(body["id"], timeout=15)
        self.assertEqual(snap["status"], "timeout")

    # -- commands
    def test_allowlisted_command_runs_and_reports_exit_code(self):
        _, body = self.call("POST", "/run", {"workspace": "proj", "command_id": "echo"})
        snap = self.wait(body["id"])
        self.assertEqual(snap["status"], "done")
        self.assertEqual(snap["exit_code"], 0)
        self.assertIn("hello from command", snap["lines"][0]["text"])
        _, body = self.call("POST", "/run", {"workspace": "proj", "command_id": "fail"})
        snap = self.wait(body["id"])
        self.assertEqual(snap["status"], "failed")
        self.assertEqual(snap["exit_code"], 7)

    def test_command_timeout(self):
        _, body = self.call("POST", "/run", {"workspace": "proj", "command_id": "slow"})
        snap = self.wait(body["id"], timeout=15)
        self.assertEqual(snap["status"], "timeout")

    def test_oversized_body_rejected(self):
        req = urllib.request.Request("http://127.0.0.1:%d/tasks" % self.port, method="POST",
                                     data=b"{" + b" " * 70000 + b"}")
        req.add_header("Authorization", "Bearer " + TOKEN)
        with self.assertRaises(urllib.error.HTTPError) as cm:
            urllib.request.urlopen(req, timeout=10)
        self.assertEqual(cm.exception.code, 400)

    def test_redactor(self):
        r = bridge.make_redactor([TOKEN])
        out = r("x Authorization: Bearer abc123 sk-ABCDEFGH12345 %s password=hunter2" % TOKEN)
        for leaked in ("abc123", "sk-ABCDEFGH12345", TOKEN, "hunter2"):
            self.assertNotIn(leaked, out)


if __name__ == "__main__":
    unittest.main(verbosity=2)
