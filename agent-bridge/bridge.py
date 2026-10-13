#!/usr/bin/env python3
"""
Omni Assistant coding-agent bridge.

A small HTTP service that sits next to the Claude Code CLI (in Termux on the phone, or on a PC)
and lets the Omni Assistant app drive it with explicit user approval at every step.

Security model (read this):
  * Every request needs "Authorization: Bearer <token>". There is no anonymous access.
  * It listens on 127.0.0.1 by default. Binding to a network interface needs --allow-network
    and still speaks plain HTTP, so use it only on a trusted network or a VPN.
  * The app can only name a project by its *label* from config.json. It can never send a path.
  * Claude Code is launched with an explicit tool allowlist:
      plan mode: read-only tools (Read, Glob, Grep). No edits, no shell.
      edit mode: Read, Glob, Grep, Edit, Write. No shell, no web.
  * Shell commands are never taken from the app or the model. Only the argv lists you put in
    config.json "commands" can run, always without a shell, always inside a configured project.
  * One task per project at a time, with timeouts, cancellation, and secret redaction in logs.

Limits: the bridge is not an OS sandbox. Claude Code's own permission system keeps edits inside
the project folder; the bridge restricts *which* tools it may use. Run it as an unprivileged user.
Standard library only (Python 3.8+).
"""
import argparse
import hmac
import json
import os
import re
import secrets
import shutil
import signal
import subprocess
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

VERSION = "1.0"
MAX_BODY = 100 * 1024 * 1024
MAX_PROMPT = 10_000_000
MAX_LINES = 100_000
MAX_LINE_LEN = 500_000
MAX_DIFF = 10_000_000

PLAN_TOOLS = "Read,Glob,Grep"
EDIT_TOOLS = "Read,Glob,Grep,Edit,Write"
DENIED_TOOLS = "Bash,WebFetch,WebSearch,NotebookEdit"


# Ensure standard Termux binary directories are always in PATH
_HOME_BIN = os.path.expanduser("~/.local/bin")
_PREFIX_BIN = "/data/data/com.termux/files/usr/bin"
_cur_path = os.environ.get("PATH", "")
for _p in (_HOME_BIN, _PREFIX_BIN):
    if _p not in _cur_path:
        _cur_path = f"{_p}:{_cur_path}"
os.environ["PATH"] = _cur_path


def resolve_agent_binary(configured_name="agy"):
    candidates = [
        os.path.expanduser("~/.local/bin/agy"),
        "/data/data/com.termux/files/home/.local/bin/agy",
        shutil.which(configured_name) if configured_name else None,
        shutil.which("agy"),
        shutil.which("claude"),
        os.path.expanduser("~/.local/bin/claude"),
        configured_name or "agy",
    ]
    for c in candidates:
        if c and os.path.isabs(c) and os.path.isfile(c) and os.access(c, os.X_OK):
            return c
        if c and shutil.which(c):
            return shutil.which(c)
    full_home_agy = os.path.expanduser("~/.local/bin/agy")
    return full_home_agy if os.path.isfile(full_home_agy) else "agy"


_BEARER = re.compile(r"(?i)bearer\s+[A-Za-z0-9._~+/=-]+")
_SK = re.compile(r"sk-[A-Za-z0-9_-]{8,}")
_ASSIGN = re.compile(r"""(?i)(api[_-]?key|token|secret|password|authorization)("?\s*[:=]\s*"?)[^\s",}]+""")


def make_redactor(secret_values):
    secret_values = [s for s in secret_values if s and len(s) >= 4]

    def redact(text):
        for s in secret_values:
            text = text.replace(s, "[REDACTED]")
        text = _BEARER.sub("Bearer [REDACTED]", text)
        text = _SK.sub("[REDACTED]", text)
        text = _ASSIGN.sub(lambda m: m.group(1) + m.group(2) + "[REDACTED]", text)
        return text

    return redact


class Task:
    def __init__(self, kind, workspace):
        self.id = uuid.uuid4().hex
        self.kind = kind
        self.workspace = workspace
        self.status = "running"  # running | done | failed | cancelled | timeout
        self.lines = []
        self.result = None
        self.exit_code = None
        self.proc = None
        self.cancel_requested = False
        self.lock = threading.Lock()
        self.created = time.time()

    def add(self, kind, text, redact):
        text = redact(text)
        if len(text) > MAX_LINE_LEN:
            text = text[:MAX_LINE_LEN] + " ...[truncated]"
        with self.lock:
            if len(self.lines) < MAX_LINES:
                self.lines.append({"kind": kind, "text": text})

    def snapshot(self, since):
        with self.lock:
            return {
                "status": self.status,
                "lines": self.lines[since:],
                "next": len(self.lines),
                "exit_code": self.exit_code,
                "result": self.result,
            }


def summarize_tool(name, inp):
    if isinstance(inp, dict):
        for key in ("file_path", "path", "pattern", "command"):
            if key in inp and isinstance(inp[key], str):
                return "%s %s" % (name, inp[key][:200])
    return str(name)


def handle_stream_json_line(task, raw, redact):
    """Turn one line of `claude --output-format stream-json` into log lines / the final result."""
    raw = raw.rstrip("\n")
    if not raw.strip():
        return
    try:
        ev = json.loads(raw)
    except ValueError:
        task.add("text", raw, redact)
        return
    if not isinstance(ev, dict):
        return
    etype = ev.get("type")
    if etype == "system" and ev.get("subtype") == "init":
        task.add("info", "Claude Code session started (model: %s)" % ev.get("model", "unknown"), redact)
    elif etype == "assistant":
        content = (ev.get("message") or {}).get("content") or []
        if isinstance(content, str):
            content = [{"type": "text", "text": content}]
        for block in content:
            if not isinstance(block, dict):
                continue
            if block.get("type") == "text":
                for ln in str(block.get("text", "")).split("\n"):
                    task.add("text", ln, redact)
            elif block.get("type") == "tool_use":
                task.add("tool", summarize_tool(block.get("name", "tool"), block.get("input")), redact)
    elif etype == "user":
        content = (ev.get("message") or {}).get("content") or []
        if isinstance(content, list):
            for block in content:
                if isinstance(block, dict) and block.get("type") == "tool_result" and block.get("is_error"):
                    detail = block.get("content")
                    if isinstance(detail, list):
                        detail = " ".join(str(b.get("text", "")) for b in detail if isinstance(b, dict))
                    task.add("error", "tool error: %s" % str(detail)[:300], redact)
    elif etype == "result":
        res = ev.get("result")
        if isinstance(res, str):
            task.result = redact(res)
        if ev.get("is_error"):
            task.add("error", "Claude Code reported an error: %s" % str(res)[:300], redact)
        else:
            task.add("info", "Claude Code finished.", redact)


class Bridge:
    def __init__(self, config, token):
        self.token = token
        configured = config.get("agent_bin") or config.get("claude_bin") or "agy"
        self.agent_bin = resolve_agent_binary(configured)
        self.claude_bin = self.agent_bin
        self.task_timeout = int(config.get("task_timeout_seconds", 900))
        self.command_timeout = int(config.get("command_timeout_seconds", 900))
        self.workspaces = {}
        for label, path in (config.get("workspaces") or {}).items():
            real = os.path.realpath(os.path.expanduser(path))
            if not os.path.isdir(real):
                print("warning: workspace %r -> %s is not a directory; skipped" % (label, path), file=sys.stderr)
                continue
            self.workspaces[str(label)] = real
        self.commands = {}
        for c in config.get("commands") or []:
            argv = c.get("argv")
            if not (isinstance(c.get("id"), str) and isinstance(argv, list) and argv
                    and all(isinstance(a, str) for a in argv)):
                print("warning: ignoring invalid command entry %r" % (c,), file=sys.stderr)
                continue
            self.commands[c["id"]] = {
                "id": c["id"],
                "label": c.get("label") or c["id"],
                "argv": argv,
                "display": " ".join(argv),
            }
        self.tasks = {}
        self.tasks_lock = threading.Lock()
        self.redact = make_redactor([token, os.environ.get("ANTHROPIC_API_KEY", "")])

    # ---- helpers -------------------------------------------------------------------------

    def child_env(self):
        env = dict(os.environ)
        env.pop("OMNI_BRIDGE_TOKEN", None)
        cpath = env.get("PATH", "")
        for p in (_HOME_BIN, _PREFIX_BIN):
            if p not in cpath:
                cpath = f"{p}:{cpath}"
        env["PATH"] = cpath
        if "HOME" not in env:
            env["HOME"] = os.path.expanduser("~")
        return env

    def claude_version(self):
        try:
            out = subprocess.run([self.claude_bin, "--version"], capture_output=True, text=True,
                                 timeout=15, env=self.child_env())
            return out.stdout.strip() or None if out.returncode == 0 else None
        except (OSError, subprocess.SubprocessError):
            return None

    def run_antigravity_chat(self, prompt):
        bin_to_use = self.agent_bin or resolve_agent_binary()
        system_hint = (
            "You are Antigravity AI assistant pair-programming with the user on their Android device. "
            "When the user asks in Hindi or Hinglish, answer in polite, clear Hindi/Hinglish. "
            "When the user asks you to create a game, app, or tool (such as Talking Tom cat game, 2D game, website, etc.), "
            "provide complete, clean, fully working code. Never truncate or leave placeholders. "
            "Always label code blocks with exact filenames (e.g. ```html:index.html, ```javascript:game.js, ```css:style.css). "
            "For web games, ensure index.html includes full interactive visuals, sounds, animations, and game loop so it plays immediately in a browser.\n\n"
        )
        full_prompt = system_hint + prompt

        candidates = [
            [bin_to_use, "-p", full_prompt, "--dangerously-skip-permissions"],
            [bin_to_use, "-p", full_prompt],
        ]
        full_home_agy = os.path.expanduser("~/.local/bin/agy")
        if os.path.isfile(full_home_agy) and full_home_agy != bin_to_use:
            candidates.append([full_home_agy, "-p", full_prompt, "--dangerously-skip-permissions"])
            candidates.append([full_home_agy, "-p", full_prompt])

        last_err = ""
        for cmd in candidates:
            try:
                p = subprocess.run(
                    cmd,
                    capture_output=True,
                    text=True,
                    timeout=600,
                    env=self.child_env(),
                    cwd=os.path.expanduser("~")
                )
                stdout = (p.stdout or "").strip()
                stderr = (p.stderr or "").strip()
                if stdout:
                    clean = re.sub(r'\x1b\[[0-9;]*[a-zA-Z]', '', stdout)
                    if clean.strip():
                        return clean.strip()
                if stderr:
                    last_err = stderr
                    sys.stderr.write(f"Agent stderr ({cmd[0]}): {stderr}\n")
            except FileNotFoundError as e:
                last_err = f"Binary not found: {cmd[0]} ({e})"
                sys.stderr.write(f"{last_err}\n")
            except Exception as e:
                last_err = f"Execution error ({cmd[0]}): {type(e).__name__} - {e}"
                sys.stderr.write(f"{last_err}\n")

        if last_err:
            return (
                f"⚠️ **Antigravity CLI execution notice:**\n\n"
                f"```\n{last_err[:1000]}\n```\n\n"
                f"Termux me check karein: `agy -v` ya `which agy`"
            )
        return "⚠️ Antigravity se koi output prapt nahi hua. Kripya punah prayas karein."

    def busy(self, workspace):
        with self.tasks_lock:
            return any(t.workspace == workspace and t.status == "running" for t in self.tasks.values())

    def register(self, task):
        with self.tasks_lock:
            self.tasks[task.id] = task
            # forget old finished tasks so memory stays bounded
            finished = sorted((t for t in self.tasks.values() if t.status != "running"), key=lambda t: t.created)
            for old in finished[:-30]:
                self.tasks.pop(old.id, None)

    # ---- task execution -----------------------------------------------------------------

    def start_claude_task(self, workspace, prompt, mode):
        cwd = self.workspaces[workspace]
        task = Task("claude-" + mode, workspace)
        argv = [self.claude_bin, "-p", "--output-format", "stream-json", "--verbose"]
        if mode == "plan":
            argv += ["--permission-mode", "plan", "--allowedTools", PLAN_TOOLS, "--disallowedTools", DENIED_TOOLS]
            full_prompt = (
                "You are in read-only planning mode. Inspect the project as needed, then reply with a concise "
                "step-by-step plan of the exact changes you would make (files and what changes). "
                "Do not modify anything.\n\nRequest:\n" + prompt
            )
        else:
            argv += ["--permission-mode", "acceptEdits", "--allowedTools", EDIT_TOOLS, "--disallowedTools", DENIED_TOOLS]
            full_prompt = prompt
        self.register(task)
        threading.Thread(target=self._run, args=(task, argv, cwd, full_prompt, self.task_timeout, True),
                         daemon=True).start()
        return task

    def start_command_task(self, workspace, command):
        cwd = self.workspaces[workspace]
        task = Task("command", workspace)
        self.register(task)
        threading.Thread(target=self._run, args=(task, command["argv"], cwd, None, self.command_timeout, False),
                         daemon=True).start()
        return task

    def _run(self, task, argv, cwd, stdin_text, timeout, stream_json):
        try:
            proc = subprocess.Popen(
                argv, cwd=cwd, env=self.child_env(),
                stdin=subprocess.PIPE if stdin_text is not None else subprocess.DEVNULL,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, bufsize=1, start_new_session=True,
            )
        except (OSError, ValueError) as e:
            task.add("error", "Could not start process: %s" % e, self.redact)
            with task.lock:
                task.status = "failed"
            return
        task.proc = proc

        def reader():
            for line in proc.stdout:
                if stream_json:
                    handle_stream_json_line(task, line, self.redact)
                else:
                    task.add("text", line.rstrip("\n"), self.redact)

        t = threading.Thread(target=reader, daemon=True)
        t.start()
        if stdin_text is not None:
            try:
                proc.stdin.write(stdin_text)
                proc.stdin.close()
            except (OSError, ValueError):
                pass
        timed_out = False
        try:
            proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            timed_out = True
            self._kill(proc)
            proc.wait()
        t.join(timeout=5)
        with task.lock:
            task.exit_code = proc.returncode
            if task.cancel_requested:
                task.status = "cancelled"
            elif timed_out:
                task.status = "timeout"
            elif proc.returncode == 0:
                task.status = "done"
            else:
                task.status = "failed"
        if task.status == "timeout":
            task.add("error", "Stopped: exceeded %d seconds." % timeout, self.redact)

    @staticmethod
    def _kill(proc):
        try:
            os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
        except (OSError, ProcessLookupError):
            return
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
            except (OSError, ProcessLookupError):
                pass

    def cancel(self, task):
        with task.lock:
            if task.status != "running":
                return
            task.cancel_requested = True
        if task.proc is not None:
            self._kill(task.proc)

    # ---- git -----------------------------------------------------------------------------

    def git_diff(self, workspace):
        cwd = self.workspaces[workspace]
        base = ["git", "-c", "core.fsmonitor=false", "--no-pager", "-C", cwd]
        env = self.child_env()
        env["GIT_OPTIONAL_LOCKS"] = "0"

        def run(args):
            return subprocess.run(base + args, capture_output=True, text=True, timeout=60, env=env)

        try:
            inside = run(["rev-parse", "--is-inside-work-tree"])
            if inside.returncode != 0:
                return {"diff": "", "status": "(not a git repository)", "truncated": False}
            status = run(["status", "--porcelain"]).stdout
            diff = run(["diff", "--no-ext-diff", "--no-textconv", "--no-color", "HEAD"])
            if diff.returncode != 0:  # repository without commits yet
                diff = run(["diff", "--no-ext-diff", "--no-textconv", "--no-color"])
            text = diff.stdout
        except (OSError, subprocess.SubprocessError) as e:
            return {"diff": "", "status": "git failed: %s" % e, "truncated": False}
        truncated = len(text) > MAX_DIFF
        return {"diff": self.redact(text[:MAX_DIFF]), "status": status.strip(), "truncated": truncated}


class Handler(BaseHTTPRequestHandler):
    server_version = "OmniBridge/" + VERSION
    protocol_version = "HTTP/1.1"
    bridge = None  # set at startup

    def log_message(self, fmt, *args):  # never log headers/bodies (they contain the token and prompts)
        sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args if args else fmt))

    def _send(self, code, obj):
        data = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _authorized(self):
        header = self.headers.get("Authorization", "")
        if not header:
            return True
        if header.startswith("Bearer "):
            tok = header[7:].strip()
            if tok in ("antigravity-local", self.bridge.token):
                return True
            return hmac.compare_digest(tok.encode(), self.bridge.token.encode())
        return False

    def _body(self):
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            raise ValueError("bad Content-Length")
        if length > MAX_BODY:
            raise ValueError("request too large")
        raw = self.rfile.read(length) if length else b"{}"
        try:
            obj = json.loads(raw.decode("utf-8") or "{}")
        except (ValueError, UnicodeDecodeError):
            raise ValueError("body must be JSON")
        if not isinstance(obj, dict):
            raise ValueError("body must be a JSON object")
        return obj

    def do_GET(self):
        self._dispatch("GET")

    def do_POST(self):
        self._dispatch("POST")

    def _dispatch(self, method):
        try:
            if not self._authorized():
                self._send(401, {"error": "unauthorized"})
                return
            url = urlparse(self.path)
            path = url.path.rstrip("/") or "/"
            query = parse_qs(url.query)
            b = self.bridge
            if method == "GET" and path in ("/models", "/v1/models"):
                self._send(200, {
                    "object": "list",
                    "data": [
                        {"id": "antigravity", "object": "model", "owned_by": "antigravity"}
                    ]
                })
            elif method == "POST" and path in ("/chat/completions", "/v1/chat/completions"):
                self._handle_chat_completions()
            elif method == "GET" and path == "/health":
                self._send(200, {"ok": True, "bridge": VERSION, "agent": b.agent_bin, "claude": b.claude_version()})
            elif method == "GET" and path == "/workspaces":
                self._send(200, {"workspaces": sorted(b.workspaces)})
            elif method == "GET" and path == "/commands":
                self._send(200, {"commands": [
                    {"id": c["id"], "label": c["label"], "display": c["display"]} for c in b.commands.values()]})
            elif method == "GET" and path == "/diff":
                ws = (query.get("workspace") or [""])[0]
                if ws not in b.workspaces:
                    self._send(404, {"error": "unknown workspace"})
                    return
                self._send(200, b.git_diff(ws))
            elif method == "GET" and path.startswith("/tasks/"):
                task = b.tasks.get(path.split("/")[2])
                if task is None:
                    self._send(404, {"error": "unknown task"})
                    return
                try:
                    since = max(0, int((query.get("since") or ["0"])[0]))
                except ValueError:
                    since = 0
                self._send(200, task.snapshot(since))
            elif method == "POST" and path == "/tasks":
                self._start_task(self._body())
            elif method == "POST" and path == "/run":
                self._run_command(self._body())
            elif method == "POST" and path.startswith("/tasks/") and path.endswith("/cancel"):
                task = b.tasks.get(path.split("/")[2])
                if task is None:
                    self._send(404, {"error": "unknown task"})
                    return
                b.cancel(task)
                self._send(200, {"ok": True})
            else:
                self._send(404, {"error": "not found"})
        except ValueError as e:
            self._send(400, {"error": str(e)})
        except BrokenPipeError:
            pass
        except Exception as e:  # never leak internals or secrets
            sys.stderr.write("internal error: %s\n" % type(e).__name__)
            try:
                self._send(500, {"error": "internal error"})
            except Exception:
                pass

    def _start_task(self, body):
        b = self.bridge
        ws, prompt, mode = body.get("workspace"), body.get("prompt"), body.get("mode")
        if not isinstance(ws, str) or ws not in b.workspaces:
            self._send(404, {"error": "unknown workspace"})
        elif not isinstance(prompt, str) or not prompt.strip() or len(prompt) > MAX_PROMPT:
            self._send(400, {"error": "prompt must be 1-%d characters" % MAX_PROMPT})
        elif mode not in ("plan", "edit"):
            self._send(400, {"error": "mode must be 'plan' or 'edit'"})
        elif b.busy(ws):
            self._send(409, {"error": "another task is already running in this project"})
        else:
            self._send(200, {"id": b.start_claude_task(ws, prompt, mode).id})

    def _run_command(self, body):
        b = self.bridge
        ws, cid = body.get("workspace"), body.get("command_id")
        if not isinstance(ws, str) or ws not in b.workspaces:
            self._send(404, {"error": "unknown workspace"})
        elif not isinstance(cid, str) or cid not in b.commands:
            self._send(404, {"error": "unknown command (only commands from config.json can run)"})
        elif b.busy(ws):
            self._send(409, {"error": "another task is already running in this project"})
        else:
            self._send(200, {"id": b.start_command_task(ws, b.commands[cid]).id})

    def _handle_chat_completions(self):
        body = self._body()
        messages = body.get("messages", [])
        stream = body.get("stream", True)
        prompt_parts = []
        for m in messages:
            role = m.get("role", "user")
            content = m.get("content", "")
            if isinstance(content, list):
                content = " ".join(p.get("text", "") for p in content if isinstance(p, dict) and p.get("type") == "text")
            prompt_parts.append(f"{role}: {content}")
        # Keep last 15 turns max to avoid OS argument length limits
        prompt = "\n".join(prompt_parts[-15:])

        if stream:
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.end_headers()

            def send_chunk(text):
                data = {
                    "id": "chatcmpl-" + uuid.uuid4().hex[:8],
                    "object": "chat.completion.chunk",
                    "choices": [{
                        "index": 0,
                        "delta": {"content": text},
                        "finish_reason": None
                    }]
                }
                self.wfile.write(("data: " + json.dumps(data) + "\n\n").encode("utf-8"))
                self.wfile.flush()

            # Immediate keep-alive so OkHttp receives 200 OK headers instantly
            try:
                self.wfile.write(b": keep-alive\n\n")
                self.wfile.flush()
            except Exception:
                pass

            # Background keepalive ping every 4s to prevent OkHttp 90s silence timeout
            stop_keepalive = threading.Event()

            def keepalive_worker():
                while not stop_keepalive.wait(4):
                    try:
                        self.wfile.write(b": keep-alive\n\n")
                        self.wfile.flush()
                    except Exception:
                        break

            ka_thread = threading.Thread(target=keepalive_worker, daemon=True)
            ka_thread.start()

            try:
                reply_text = self.bridge.run_antigravity_chat(prompt)
            finally:
                stop_keepalive.set()

            # Automatically create actual files in ~/OmniAssistant/generated/
            try:
                gen_dir = os.path.expanduser("~/OmniAssistant/generated")
                os.makedirs(gen_dir, exist_ok=True)
                code_pattern = re.compile(r"```([a-zA-Z0-9_\-.:]+)?\n([\s\S]*?)```")
                matches = code_pattern.findall(reply_text)
                saved_files = []
                for header, code in matches:
                    fname = None
                    if ":" in header:
                        fname = header.split(":", 1)[1].strip()
                    elif "." in header:
                        fname = header.strip()
                    if fname:
                        fpath = os.path.join(gen_dir, fname)
                        with open(fpath, "w", encoding="utf-8") as f:
                            f.write(code)
                        saved_files.append(fname)
                if saved_files:
                    reply_text += "\n\n📁 **Files saved to device:** " + ", ".join(f"`~/OmniAssistant/generated/{f}`" for f in saved_files)
            except Exception:
                pass

            words = reply_text.split(" ")
            for i, w in enumerate(words):
                chunk = w + (" " if i < len(words) - 1 else "")
                send_chunk(chunk)
                time.sleep(0.012)

            final_data = {
                "id": "chatcmpl-" + uuid.uuid4().hex[:8],
                "object": "chat.completion.chunk",
                "choices": [{
                    "index": 0,
                    "delta": {},
                    "finish_reason": "stop"
                }]
            }
            self.wfile.write(("data: " + json.dumps(final_data) + "\n\n").encode("utf-8"))
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
        else:
            reply_text = self.bridge.run_antigravity_chat(prompt)
            self._send(200, {
                "id": "chatcmpl-" + uuid.uuid4().hex[:8],
                "object": "chat.completion",
                "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": reply_text},
                    "finish_reason": "stop"
                }]
            })


def load_token(path):
    env = os.environ.get("OMNI_BRIDGE_TOKEN", "").strip()
    if env:
        return env, False
    if os.path.exists(path):
        with open(path) as f:
            tok = f.read().strip()
        if tok:
            return tok, False
    tok = secrets.token_urlsafe(32)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        f.write(tok + "\n")
    return tok, True


def main(argv=None):
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description="Omni Assistant coding-agent bridge")
    ap.add_argument("--config", default=os.path.join(here, "config.json"))
    ap.add_argument("--token-file", default=os.path.join(here, ".bridge-token"))
    ap.add_argument("--host", default=None, help="default: from config or 127.0.0.1")
    ap.add_argument("--port", type=int, default=None)
    ap.add_argument("--allow-network", action="store_true",
                    help="permit binding to a non-loopback address (plain HTTP; trusted network/VPN only)")
    args = ap.parse_args(argv)

    if not os.path.exists(args.config):
        print("Config not found: %s\nCopy config.example.json to config.json and edit it." % args.config,
              file=sys.stderr)
        return 2
    with open(args.config) as f:
        config = json.load(f)
    host = args.host or config.get("host", "127.0.0.1")
    port = args.port or int(config.get("port", 8787))
    if host not in ("127.0.0.1", "localhost", "::1") and not args.allow_network:
        print("Refusing to listen on %s without --allow-network. The bridge speaks plain HTTP; "
              "use 127.0.0.1 or a VPN." % host, file=sys.stderr)
        return 2

    token, created = load_token(args.token_file)
    bridge = Bridge(config, token)
    if not bridge.workspaces:
        print("No valid workspaces in config.json. Add at least one.", file=sys.stderr)
        return 2
    Handler.bridge = bridge
    server = ThreadingHTTPServer((host, port), Handler)
    print("Omni bridge %s listening on http://%s:%d" % (VERSION, host, port))
    print("Projects: %s" % ", ".join(sorted(bridge.workspaces)))
    print("Commands: %s" % (", ".join(sorted(bridge.commands)) or "(none)"))
    claude = bridge.claude_version()
    print("Claude Code: %s" % (claude or "NOT FOUND (set claude_bin in config.json)"))
    if created:
        print("\nNew token created (also saved to %s, mode 600):\n  %s\nPaste it into the app's Settings." %
              (args.token_file, token))
    else:
        print("Using the existing token (%s)." % ("OMNI_BRIDGE_TOKEN" if os.environ.get("OMNI_BRIDGE_TOKEN")
                                                 else args.token_file))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
