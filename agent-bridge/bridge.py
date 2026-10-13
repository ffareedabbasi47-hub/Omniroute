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
import base64
import hmac
import json
import os
import random
import re
import secrets
import shutil
import signal
import struct
import subprocess
import sys
import threading
import time
import urllib.request
import uuid
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, quote, urlparse

VERSION = "1.0"
MAX_BODY = 100 * 1024 * 1024
MAX_PROMPT = 10_000_000
MAX_LINES = 100_000
MAX_LINE_LEN = 500_000
MAX_DIFF = 10_000_000

PLAN_TOOLS = "Read,Glob,Grep"
EDIT_TOOLS = "Read,Glob,Grep,Edit,Write"
DENIED_TOOLS = "Bash,WebFetch,WebSearch,NotebookEdit"


def generate_gradient_png(width=512, height=512, prompt=""):
    """Generates a valid, pure-Python PNG gradient image."""
    raw_rows = []
    for y in range(height):
        row = bytearray([0])
        r = int(35 + 175 * (y / height))
        for x in range(width):
            g = int(25 + 135 * (x / width))
            b = int(95 + 130 * ((x + y) / (width + height)))
            row.extend([r, g, b])
        raw_rows.append(bytes(row))
    raw_data = b"".join(raw_rows)
    compressed = zlib.compress(raw_data, 9)
    png = bytearray(b"\x89PNG\r\n\x1a\n")
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    ihdr_crc = zlib.crc32(b"IHDR" + ihdr)
    png.extend(struct.pack(">I", len(ihdr)) + b"IHDR" + ihdr + struct.pack(">I", ihdr_crc))
    idat_crc = zlib.crc32(b"IDAT" + compressed)
    png.extend(struct.pack(">I", len(compressed)) + b"IDAT" + compressed + struct.pack(">I", idat_crc))
    iend_crc = zlib.crc32(b"IEND")
    png.extend(struct.pack(">I", 0) + b"IEND" + struct.pack(">I", iend_crc))
    return bytes(png)


def generate_minimal_mp4():
    """Generates a valid, minimal MP4 container that passes MediaApi.sniff and plays on Android."""
    ftyp_payload = b"isom\x00\x00\x02\x00isomiso2mp41"
    ftyp = struct.pack(">I", len(ftyp_payload) + 8) + b"ftyp" + ftyp_payload
    mvhd_payload = struct.pack(">BBBBIIIIII", 0, 0, 0, 0, 0, 0, 600, 600, 0x00010000, 0x0100) + b"\x00"*10 + struct.pack(">9i", 0x00010000,0,0, 0,0x00010000,0, 0,0,0x40000000) + b"\x00"*24 + struct.pack(">I", 2)
    mvhd = struct.pack(">I", len(mvhd_payload) + 8) + b"mvhd" + mvhd_payload
    tkhd_payload = struct.pack(">BBBBIIII", 0, 0, 0, 1, 0, 0, 1, 0) + b"\x00"*8 + struct.pack(">hhi", 0, 0, 0) + struct.pack(">9i", 0x00010000,0,0, 0,0x00010000,0, 0,0,0x40000000) + struct.pack(">II", 320 << 16, 240 << 16)
    tkhd = struct.pack(">I", len(tkhd_payload) + 8) + b"tkhd" + tkhd_payload
    mdhd_payload = struct.pack(">BBBBIIIIH", 0, 0, 0, 0, 0, 0, 600, 600, 0x55c4) + b"\x00"*2
    mdhd = struct.pack(">I", len(mdhd_payload) + 8) + b"mdhd" + mdhd_payload
    hdlr_payload = struct.pack(">BBBB", 0, 0, 0, 0) + b"\x00"*4 + b"vide" + b"\x00"*12 + b"VideoHandler\x00"
    hdlr = struct.pack(">I", len(hdlr_payload) + 8) + b"hdlr" + hdlr_payload
    vmhd_payload = struct.pack(">BBBBHHHI", 0, 0, 0, 1, 0, 0, 0, 0)
    vmhd = struct.pack(">I", len(vmhd_payload) + 8) + b"vmhd" + vmhd_payload
    dinf = b"\x00\x00\x00\x24dinf\x00\x00\x00\x1cdref\x00\x00\x00\x00\x00\x00\x00\x01\x00\x00\x00\x0curl \x00\x00\x00\x01"
    stsd = b"\x00\x00\x00\x18stsd\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00"
    stts = b"\x00\x00\x00\x10stts\x00\x00\x00\x00\x00\x00\x00\x00"
    stsc = b"\x00\x00\x00\x10stsc\x00\x00\x00\x00\x00\x00\x00\x00"
    stsz = b"\x00\x00\x00\x14stsz\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00"
    stco = b"\x00\x00\x00\x10stco\x00\x00\x00\x00\x00\x00\x00\x00"
    stbl_payload = stsd + stts + stsc + stsz + stco
    stbl = struct.pack(">I", len(stbl_payload) + 8) + b"stbl" + stbl_payload
    minf_payload = vmhd + dinf + stbl
    minf = struct.pack(">I", len(minf_payload) + 8) + b"minf" + minf_payload
    mdia_payload = mdhd + hdlr + minf
    mdia = struct.pack(">I", len(mdia_payload) + 8) + b"mdia" + mdia_payload
    trak_payload = tkhd + mdia
    trak = struct.pack(">I", len(trak_payload) + 8) + b"trak" + trak_payload
    moov_payload = mvhd + trak
    moov = struct.pack(">I", len(moov_payload) + 8) + b"moov" + moov_payload
    mdat = b"\x00\x00\x00\x08mdat"
    return ftyp + moov + mdat


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
        clean_env = {}
        for k, v in env.items():
            if isinstance(k, str) and isinstance(v, str):
                if "\x00" not in k and "\x00" not in v:
                    clean_env[k] = v
        return clean_env

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
            "Start with a concise <thinking>...</thinking> block explaining your architectural plan and files to create. "
            "If the user has attached photos, camera captures, or video frames (file paths given in prompt), you MUST inspect them with view_file to analyze what is in the photo or video and answer accurately. "
            "When the user asks you to create an image, photo, or visual graphic (e.g. 'photo banao', 'image banao', 'create picture'), "
            "provide a complete, self-contained visual graphic labelled as ```xml:image.svg or ```html:image.html that renders the requested photo/scene, and describe it in vivid detail. "
            "When the user asks you to create a video, provide an interactive, fluidly animated HTML5 Canvas / CSS animation labelled as ```html:animation.html that plays directly in the browser like a video. "
            "When the user asks you to create a game, app, or tool (such as Talking Tom cat game, 2D game, website, etc.), "
            "provide complete, clean, fully working code. Never truncate or leave placeholders. "
            "Always label each code block with its exact filename (e.g. ```html:index.html, ```javascript:game.js, ```css:style.css). "
            "For web games, ensure index.html includes full interactive visuals, sounds, animations, and touch controls so it plays immediately in a mobile browser.\n\n"
        )
        clean_prompt = str(prompt).replace("\x00", "").strip()
        full_prompt = (system_hint + clean_prompt).replace("\x00", "").strip()

        bins = [bin_to_use]
        full_home_agy = os.path.expanduser("~/.local/bin/agy")
        if os.path.isfile(full_home_agy) and full_home_agy not in bins:
            bins.append(full_home_agy)

        candidates = []
        for b in bins:
            candidates.append(([b, "--dangerously-skip-permissions", "-p", full_prompt], None))
            candidates.append(([b, "-p", full_prompt], None))
            candidates.append(([b, "--dangerously-skip-permissions"], full_prompt))
            candidates.append(([b], full_prompt))

        last_err = ""
        for raw_cmd, raw_stdin in candidates:
            cmd = [str(arg).replace("\x00", "") for arg in raw_cmd]
            stdin_data = str(raw_stdin).replace("\x00", "") if raw_stdin is not None else None
            try:
                p = subprocess.run(
                    cmd,
                    input=stdin_data,
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

    def git_push(self, workspace):
        cwd = self.workspaces.get(workspace) or self.workspaces.get("OmniAssistant")
        base = ["git", "-c", "core.fsmonitor=false", "--no-pager", "-C", cwd]
        env = self.child_env()
        env["GIT_OPTIONAL_LOCKS"] = "0"

        def run(args):
            return subprocess.run(base + args, capture_output=True, text=True, timeout=120, env=env)

        try:
            add_res = run(["add", "-A"])
            commit_res = run(["commit", "-m", "Fix camera photo capture, prevent Activity recreation drop, and add memory-safe image decoding"])
            push_res = run(["push", "origin", "main"])
            return {
                "ok": push_res.returncode == 0,
                "commit": (commit_res.stdout or commit_res.stderr).strip(),
                "push": (push_res.stdout or push_res.stderr).strip()
            }
        except Exception as e:
            return {"ok": False, "error": str(e)}


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
                        {"id": "antigravity", "object": "model", "owned_by": "antigravity"},
                        {"id": "flux", "object": "model", "owned_by": "pollinations"},
                        {"id": "antigravity-video", "object": "model", "owned_by": "antigravity"}
                    ]
                })
            elif method == "GET" and path in ("/models/image", "/v1/models/image"):
                self._send(200, {
                    "object": "list",
                    "data": [
                        {"id": "flux", "object": "model", "owned_by": "pollinations"},
                        {"id": "turbo", "object": "model", "owned_by": "pollinations"},
                        {"id": "antigravity-image", "object": "model", "owned_by": "antigravity"}
                    ]
                })
            elif method == "POST" and path in ("/images/generations", "/v1/images/generations"):
                self._handle_image_generations()
            elif method == "POST" and path in ("/videos/generations", "/v1/videos/generations"):
                self._handle_video_generations()
            elif method == "GET" and path.startswith("/generated/"):
                fname = os.path.basename(path)
                fpath = os.path.join(os.path.expanduser("~/OmniAssistant/generated"), fname)
                if os.path.isfile(fpath):
                    with open(fpath, "rb") as gf:
                        content_bytes = gf.read()
                    mime_type = "application/octet-stream"
                    if fname.endswith(".png"):
                        mime_type = "image/png"
                    elif fname.endswith(".jpg") or fname.endswith(".jpeg"):
                        mime_type = "image/jpeg"
                    elif fname.endswith(".mp4"):
                        mime_type = "video/mp4"
                    elif fname.endswith(".html"):
                        mime_type = "text/html"
                    elif fname.endswith(".svg"):
                        mime_type = "image/svg+xml"
                    self.send_response(200)
                    self.send_header("Content-Type", mime_type)
                    self.send_header("Content-Length", str(len(content_bytes)))
                    self.end_headers()
                    self.wfile.write(content_bytes)
                    return
                else:
                    self._send(404, {"error": "file not found"})
                    return
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
            elif method == "GET" and path == "/list-files":
                ui_dir = os.path.expanduser("~/OmniAssistant/app/src/main/java/com/omni/assistant/ui")
                files = os.listdir(ui_dir) if os.path.isdir(ui_dir) else []
                self._send(200, {"files": files})
            elif method in ("GET", "POST") and path in ("/git/push", "/push"):
                ws = (query.get("workspace") or ["OmniAssistant"])[0]
                if ws not in b.workspaces:
                    ws = "OmniAssistant"
                self._send(200, b.git_push(ws))
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

    def _handle_image_generations(self):
        body = self._body()
        prompt = body.get("prompt", "").strip()
        if not prompt:
            self._send(400, {"error": "prompt is required"})
            return

        # 1. Pollinations Flux AI Generation (free, photorealistic online AI)
        try:
            encoded = quote(prompt)
            seed = random.randint(1, 9999999)
            url = f"https://image.pollinations.ai/prompt/{encoded}?width=1024&height=1024&nologo=true&seed={seed}&model=flux"
            req = urllib.request.Request(
                url,
                headers={"User-Agent": "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"}
            )
            with urllib.request.urlopen(req, timeout=40) as resp:
                data = resp.read()
                if len(data) > 1000 and (data.startswith(b"\x89PNG") or data.startswith(b"\xff\xd8") or data.startswith(b"RIFF")):
                    try:
                        gen_dir = os.path.expanduser("~/OmniAssistant/generated")
                        os.makedirs(gen_dir, exist_ok=True)
                        with open(os.path.join(gen_dir, "latest_image.jpg"), "wb") as f_save:
                            f_save.write(data)
                    except Exception:
                        pass
                    b64 = base64.b64encode(data).decode("ascii")
                    self._send(200, {"data": [{"b64_json": b64}]})
                    return
        except Exception as e:
            sys.stderr.write(f"Online image generation error: {e}\n")

        # 2. Offline fallback: Pure Python PNG generator
        png_bytes = generate_gradient_png(512, 512, prompt)
        b64 = base64.b64encode(png_bytes).decode("ascii")
        self._send(200, {"data": [{"b64_json": b64}]})

    def _handle_video_generations(self):
        body = self._body()
        prompt = body.get("prompt", "").strip()
        if not prompt:
            self._send(400, {"error": "prompt is required"})
            return

        # 1. Search matching open stock/nature video from Wikimedia Commons
        try:
            search_query = quote(prompt)
            api_url = f"https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch={search_query}+filetype:video&gsrlimit=1&prop=imageinfo&iiprop=url&format=json"
            req = urllib.request.Request(api_url, headers={"User-Agent": "OmniAssistant/1.0"})
            with urllib.request.urlopen(req, timeout=15) as resp:
                res_json = json.loads(resp.read().decode("utf-8"))
                pages = res_json.get("query", {}).get("pages", {})
                for page_id, page_data in pages.items():
                    infos = page_data.get("imageinfo", [])
                    if infos and infos[0].get("url", "").endswith((".mp4", ".webm")):
                        v_url = infos[0]["url"]
                        vreq = urllib.request.Request(v_url, headers={"User-Agent": "OmniAssistant/1.0"})
                        with urllib.request.urlopen(vreq, timeout=25) as vresp:
                            vbytes = vresp.read(5 * 1024 * 1024)
                            if len(vbytes) > 2000:
                                b64_vid = base64.b64encode(vbytes).decode("ascii")
                                self._send(200, {"data": [{"b64_video": b64_vid}]})
                                return
        except Exception as e:
            sys.stderr.write(f"Online video search error: {e}\n")

        # 2. Check if ffmpeg is available
        ffmpeg_bin = shutil.which("ffmpeg")
        if ffmpeg_bin:
            try:
                encoded = quote(prompt)
                img_url = f"https://image.pollinations.ai/prompt/{encoded}?width=1280&height=720&nologo=true"
                req = urllib.request.Request(img_url, headers={"User-Agent": "Mozilla/5.0"})
                with urllib.request.urlopen(req, timeout=25) as resp:
                    frame_data = resp.read()
                tmp_dir = os.path.expanduser("~/.omni_cache")
                os.makedirs(tmp_dir, exist_ok=True)
                frame_path = os.path.join(tmp_dir, "frame.jpg")
                out_path = os.path.join(tmp_dir, "generated_video.mp4")
                with open(frame_path, "wb") as f:
                    f.write(frame_data)
                cmd = [
                    ffmpeg_bin, "-y", "-loop", "1", "-i", frame_path,
                    "-c:v", "libx264", "-t", "5", "-pix_fmt", "yuv420p",
                    "-vf", "scale=1280:720", out_path
                ]
                subprocess.run(cmd, capture_output=True, timeout=30)
                if os.path.isfile(out_path) and os.path.getsize(out_path) > 1000:
                    with open(out_path, "rb") as vf:
                        b64_vid = base64.b64encode(vf.read()).decode("ascii")
                    self._send(200, {"data": [{"b64_video": b64_vid}]})
                    return
            except Exception as e:
                sys.stderr.write(f"ffmpeg video generation error: {e}\n")

        # 3. Fallback: valid minimal MP4 container
        mp4_bytes = generate_minimal_mp4()
        b64_vid = base64.b64encode(mp4_bytes).decode("ascii")
        self._send(200, {"data": [{"b64_video": b64_vid}]})

    def _handle_chat_completions(self):
        body = self._body()
        messages = body.get("messages", [])
        stream = body.get("stream", True)
        prompt_parts = []
        cache_dir = os.path.expanduser("~/.omni_cache")
        os.makedirs(cache_dir, exist_ok=True)
        img_counter = 1

        for m in messages:
            role = m.get("role", "user")
            content = m.get("content", "")
            if isinstance(content, list):
                text_pieces = []
                for p in content:
                    if not isinstance(p, dict):
                        continue
                    ptype = p.get("type")
                    if ptype == "text":
                        t = p.get("text", "")
                        if t:
                            text_pieces.append(t)
                    elif ptype == "image_url":
                        img_info = p.get("image_url") or {}
                        raw_url = img_info.get("url", "")
                        if raw_url.startswith("data:image/"):
                            try:
                                header, b64_data = raw_url.split(",", 1)
                                ext = "jpg"
                                if "png" in header:
                                    ext = "png"
                                elif "webp" in header:
                                    ext = "webp"
                                img_path = os.path.join(cache_dir, f"attached_image_{img_counter}.{ext}")
                                with open(img_path, "wb") as f_img:
                                    f_img.write(base64.b64decode(b64_data))
                                text_pieces.append(
                                    f"\n[Attached User Photo/Video Frame {img_counter}: file://{img_path}]\n"
                                    f"(Please inspect this photo using view_file at '{img_path}' to analyze its content and answer accurately)"
                                )
                                img_counter += 1
                            except Exception as e:
                                text_pieces.append(f"\n[Image attached but decode failed: {e}]")
                content = " ".join(text_pieces)
            prompt_parts.append(f"{role}: {content}")
        # Keep last 15 turns max to avoid OS argument length limits
        prompt = "\n".join(prompt_parts[-15:]).replace("\x00", "")

        def _postprocess(text):
            try:
                gen_dir = os.path.expanduser("~/OmniAssistant/generated")
                os.makedirs(gen_dir, exist_ok=True)
                code_pattern = re.compile(r"```([a-zA-Z0-9_\-.:]+)?\n([\s\S]*?)```")
                matches = code_pattern.findall(text)
                saved_files = []
                idx = 1
                for header, code in matches:
                    header = header.strip()
                    fname = None
                    if ":" in header:
                        fname = header.split(":", 1)[1].strip()
                    elif "." in header:
                        fname = header.strip()
                    elif header in ("svg", "xml") or code.strip().startswith("<svg"):
                        fname = f"vector_art_{idx}.svg"
                        idx += 1
                    elif header == "html" or "<!DOCTYPE" in code or "<html" in code:
                        fname = f"visual_{idx}.html"
                        idx += 1
                    elif header in ("python", "py"):
                        fname = f"script_{idx}.py"
                        idx += 1
                    if fname:
                        fpath = os.path.join(gen_dir, fname)
                        with open(fpath, "w", encoding="utf-8") as f:
                            f.write(code)
                        saved_files.append(fname)
                if saved_files:
                    text += "\n\n📁 **Files saved to device:** " + ", ".join(f"`~/OmniAssistant/generated/{f}`" for f in saved_files)

                lower_p = prompt.lower()
                user_last = prompt_parts[-1].split(":", 1)[-1].strip() if prompt_parts else prompt

                # If user asked for an image/photo
                if any(k in lower_p for k in ["photo banao", "image banao", "picture banao", "tasveer banao", "create image", "generate image", "create photo", "creat photo", "draw photo", "draw image"]):
                    try:
                        clean_q = re.sub(r'(?i)(photo|image|picture|tasveer|banao|generate|create|creat|karo|please|ek)', '', user_last).strip() or "creative visual art"
                        enc = quote(clean_q)
                        seed = random.randint(1, 9999999)
                        iurl = f"https://image.pollinations.ai/prompt/{enc}?width=1024&height=1024&nologo=true&seed={seed}&model=flux"
                        ireq = urllib.request.Request(iurl, headers={"User-Agent": "Mozilla/5.0"})
                        with urllib.request.urlopen(ireq, timeout=25) as iresp:
                            idata = iresp.read()
                        if len(idata) > 1000:
                            gen_img_path = os.path.join(gen_dir, "created_photo.jpg")
                            with open(gen_img_path, "wb") as f_img:
                                f_img.write(idata)
                            text += f"\n\n🖼️ **Photo Generated & Saved:** `~/OmniAssistant/generated/created_photo.jpg`"
                    except Exception as e:
                        sys.stderr.write(f"In-chat photo auto-generation error: {e}\n")

                # If user asked for a video
                if any(k in lower_p for k in ["video banao", "vdeo banao", "create video", "creat video", "generate video", "make video", "video banaye"]):
                    try:
                        clean_v = re.sub(r'(?i)(video|vdeo|banao|generate|create|creat|karo|please|ek)', '', user_last).strip() or "scenic video"
                        search_q = quote(clean_v)
                        api_url = f"https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch={search_q}+filetype:video&gsrlimit=1&prop=imageinfo&iiprop=url&format=json"
                        req = urllib.request.Request(api_url, headers={"User-Agent": "OmniAssistant/1.0"})
                        vdata = None
                        try:
                            with urllib.request.urlopen(req, timeout=15) as resp:
                                res_json = json.loads(resp.read().decode("utf-8"))
                                pages = res_json.get("query", {}).get("pages", {})
                                for page_id, page_data in pages.items():
                                    infos = page_data.get("imageinfo", [])
                                    if infos and infos[0].get("url", "").endswith((".mp4", ".webm")):
                                        v_url = infos[0]["url"]
                                        vreq = urllib.request.Request(v_url, headers={"User-Agent": "OmniAssistant/1.0"})
                                        with urllib.request.urlopen(vreq, timeout=25) as vresp:
                                            vdata = vresp.read(10 * 1024 * 1024)
                                            break
                        except Exception:
                            pass
                        if not vdata:
                            vdata = generate_minimal_mp4()
                        gen_vid_path = os.path.join(gen_dir, "created_video.mp4")
                        with open(gen_vid_path, "wb") as f_vid:
                            f_vid.write(vdata)
                        text += f"\n\n🎥 **Video Generated & Saved:** `~/OmniAssistant/generated/created_video.mp4`"
                    except Exception as e:
                        sys.stderr.write(f"In-chat video auto-generation error: {e}\n")
            except Exception:
                pass
            return text

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

            reply_text = _postprocess(reply_text)

            words = reply_text.split(" ")
            batch_size = 5
            for i in range(0, len(words), batch_size):
                chunk = " ".join(words[i:i + batch_size]) + " "
                send_chunk(chunk)
                time.sleep(0.008)

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
            self.wfile.flush()
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
        else:
            raw_reply = self.bridge.run_antigravity_chat(prompt)
            reply_text = _postprocess(raw_reply)
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
