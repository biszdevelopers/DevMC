#!/usr/bin/env python3
"""devmc MCP server: control the DevMC test Minecraft server over RCON.

Also supports a CLI mode so scripts can drive the same tools directly:
    python server.py --cli command "tps"
    python server.py --cli probe_chunk 4 4
    python server.py --cli probe_block 64 64 64
    python server.py --cli log_tail 50
"""

import json
import socket
import struct
import sys
import os
import shutil
import subprocess
import time

MCP_PROTOCOL_VERSION = "2024-11-05"


def read_config():
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.json")
    cfg = {
        "host": "127.0.0.1",
        "port": 25575,
        "password": "devmc123",
        "log_path": r"Y:\Games\Minecraft\devServer\logs\latest.log",
        "server_dir": r"Y:\Games\Minecraft\devServer",
        "plugins_dir": r"Y:\Games\Minecraft\devServer\plugins",
        "workspace": r"Y:\projects\DevMC",
        "maven": "mvn",
        "java": "java",
        "java_args": ["-Xms1G", "-Xmx2G"],
        "jar": "paper.jar",
        "ready_marker": "Done (",
        "ready_timeout": 180,
        "stop_timeout": 120,
        "build_timeout": 600,
    }
    try:
        with open(path, "r", encoding="utf-8") as f:
            cfg.update(json.load(f))
    except (OSError, ValueError):
        pass
    return cfg


class Rcon:
    def __init__(self, cfg):
        self.cfg = cfg
        self.sock = None
        self.request_id = 1

    def _packet(self, req_id, ptype, payload):
        data = (
            struct.pack("<ii", req_id, ptype)
            + payload.encode("utf-8")
            + b"\x00\x00"
        )
        return struct.pack("<i", len(data)) + data

    def _recv_packet(self):
        header = self.sock.recv(4)
        if len(header) < 4:
            raise RuntimeError("RCON connection closed")
        size = struct.unpack("<i", header)[0]
        data = b""
        while len(data) < size:
            chunk = self.sock.recv(size - len(data))
            if not chunk:
                break
            data += chunk
        req_id, ptype = struct.unpack("<ii", data[:8])
        return req_id, ptype, data[8:]

    def connect(self):
        self.sock = socket.create_connection(
            (self.cfg["host"], self.cfg["port"]), timeout=10
        )
        self.sock.settimeout(120)
        self.sock.sendall(self._packet(self.request_id, 3, self.cfg["password"]))
        req_id, ptype, _ = self._recv_packet()
        self.request_id += 1
        if req_id == -1 or ptype != 2:
            raise RuntimeError("RCON authentication failed")

    def command(self, cmd):
        self.sock.sendall(self._packet(self.request_id, 2, cmd))
        while True:
            req_id, _, payload = self._recv_packet()
            if req_id == self.request_id:
                self.request_id += 1
                return payload.rstrip(b"\x00").decode("utf-8", "replace")

    def close(self):
        if self.sock:
            try:
                self.sock.close()
            except OSError:
                pass
            self.sock = None


def _rcon_ok(cfg):
    rcon = Rcon(cfg)
    try:
        rcon.connect()
        return True
    except Exception:  # noqa: BLE001
        return False
    finally:
        rcon.close()


def _server_status(cfg):
    return "running" if _rcon_ok(cfg) else "stopped"


def _wait_ready(cfg, timeout):
    deadline = time.time() + float(timeout)
    while time.time() < deadline:
        if _rcon_ok(cfg):
            return True
        time.sleep(2)
    return False


def _server_start(cfg):
    logs = os.path.join(cfg["server_dir"], "logs")
    os.makedirs(logs, exist_ok=True)
    args = (
        [cfg.get("java", "java")]
        + list(cfg.get("java_args", []))
        + ["-jar", cfg.get("jar", "paper.jar"), "--nogui"]
    )
    creationflags = 0
    if os.name == "nt":
        creationflags = (
            subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS
        )
    stdout = open(os.path.join(logs, "console.out.log"), "ab")
    stderr = open(os.path.join(logs, "console.err.log"), "ab")
    process = subprocess.Popen(
        args,
        cwd=cfg["server_dir"],
        stdout=stdout,
        stderr=stderr,
        stdin=subprocess.DEVNULL,
        creationflags=creationflags,
        close_fds=True,
    )
    return process.pid


def _server_stop(cfg, timeout):
    try:
        rcon = Rcon(cfg)
        rcon.connect()
        rcon.command("stop")
        rcon.close()
    except Exception:  # noqa: BLE001
        pass
    deadline = time.time() + float(timeout)
    while time.time() < deadline:
        if not _rcon_ok(cfg):
            return True
        time.sleep(2)
    return not _rcon_ok(cfg)


def _deploy(cfg, source, name):
    if not source or not os.path.isfile(source):
        return "source jar not found: %s" % source
    name = name or os.path.basename(source)
    plugins = cfg.get("plugins_dir", os.path.join(cfg["server_dir"], "plugins"))
    dest = os.path.join(plugins, name)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    shutil.copy2(source, dest)
    return "deployed %s (%d bytes) -> %s" % (
        os.path.basename(source),
        os.path.getsize(dest),
        dest,
    )


def _summarize_build(stdout, stderr):
    """Condense a Maven run to its result plus the error lines only."""
    text = (stdout or "") + "\n" + (stderr or "")
    lines = text.splitlines()
    summary = next(
        (
            line.strip()
            for line in lines
            if "BUILD SUCCESS" in line or "BUILD FAILURE" in line
        ),
        None,
    )
    if summary is None:
        summary = "BUILD UNKNOWN (no result line)"
    if "BUILD SUCCESS" in summary:
        return summary
    errors = []
    for line in lines:
        if "[ERROR]" in line or line.strip().startswith("error:"):
            cleaned = line.strip()
            if cleaned not in errors:
                errors.append(cleaned)
        if len(errors) >= 40:
            break
    if not errors:
        errors = [line for line in lines[-15:]]
    return summary + "\n" + "\n".join(errors)


def _module_dir(cfg, module):
    workspace = cfg["workspace"]
    if module in (None, "", "all", "root"):
        return workspace
    return os.path.join(workspace, module)


def _build(cfg, module, clean, skip_tests, goals, install):
    directory = _module_dir(cfg, module)
    pom = os.path.join(directory, "pom.xml")
    if not os.path.isfile(pom):
        return "no pom.xml at %s" % pom
    args = [cfg.get("maven", "mvn"), "-f", pom]
    if clean:
        args.append("clean")
    args.append(goals or ("install" if install else "package"))
    if skip_tests:
        args.append("-DskipTests")
    try:
        proc = subprocess.run(
            subprocess.list2cmdline(args),
            cwd=directory,
            shell=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=float(cfg.get("build_timeout", 600)),
        )
    except subprocess.TimeoutExpired:
        return "BUILD TIMEOUT after %ss" % cfg.get("build_timeout", 600)
    except OSError as e:
        return "build failed to start: %s" % e
    return _summarize_build(proc.stdout, proc.stderr)


def _find_jar(cfg, module):
    target = os.path.join(_module_dir(cfg, module), "target")
    if not os.path.isdir(target):
        return None
    candidates = []
    for name in os.listdir(target):
        if not name.endswith(".jar"):
            continue
        if name.startswith("original-") or "sources" in name or "javadoc" in name:
            continue
        if module and not name.startswith(module + "-"):
            continue
        candidates.append(os.path.join(target, name))
    if not candidates:
        return None
    return max(candidates, key=os.path.getmtime)


def _rebuild(cfg, module, clean, skip_tests, timeout):
    if not module:
        return "rebuild needs a module, e.g. plugin-worldgen"
    build = _build(cfg, module, clean, skip_tests, None, True)
    if "BUILD SUCCESS" not in build:
        return "build failed; not deploying or restarting\n" + build
    jar = _find_jar(cfg, module)
    if not jar:
        return "build succeeded but no jar found in %s/target" % module
    deployed = _deploy(cfg, jar, None)
    stopped = _server_stop(cfg, cfg["stop_timeout"])
    pid = _server_start(cfg)
    ready = _wait_ready(cfg, timeout)
    return "\n".join(
        [build, deployed, "stopped=%s started pid=%s ready=%s" % (stopped, pid, ready)]
    )


def _log_grep(cfg, pattern, lines, ignore_case):
    import re

    flags = re.IGNORECASE if ignore_case else 0
    try:
        regex = re.compile(pattern, flags)
    except re.error as e:
        return "bad regex: %s" % e
    matches = []
    try:
        with open(cfg["log_path"], "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                if regex.search(line):
                    matches.append(line.rstrip("\n"))
    except OSError as e:
        return "cannot read log: %s" % e
    if not matches:
        return "no matches for %r" % pattern
    shown = matches[-int(lines):]
    return "%d match(es), showing last %d:\n%s" % (
        len(matches),
        len(shown),
        "\n".join(shown),
    )


def _wait_log(cfg, pattern, timeout, ignore_case):
    import re

    flags = re.IGNORECASE if ignore_case else 0
    try:
        regex = re.compile(pattern, flags)
    except re.error as e:
        return "bad regex: %s" % e
    log = cfg["log_path"]
    start = os.path.getsize(log) if os.path.exists(log) else 0
    deadline = time.time() + float(timeout)
    while time.time() < deadline:
        try:
            with open(log, "r", encoding="utf-8", errors="replace") as f:
                f.seek(start)
                for line in f:
                    if regex.search(line):
                        return "matched: " + line.rstrip("\n")
        except OSError:
            pass
        time.sleep(2)
    return "timeout after %ss waiting for %r" % (timeout, pattern)


def _command_batch(cfg, cmds):
    rcon = Rcon(cfg)
    rcon.connect()
    try:
        out = []
        for cmd in cmds:
            try:
                result = rcon.command(cmd)
            except Exception as e:  # noqa: BLE001
                result = "error: %s" % e
            out.append("%s => %s" % (cmd, result.replace("\n", " | ")))
        return "\n".join(out)
    finally:
        rcon.close()


def tool_specs():
    return [
        {
            "name": "command",
            "description": (
                "Run a console command on the DevMC test server via RCON and "
                "return the output. Use for tps, worldinfo, seed, etc."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "cmd": {
                        "type": "string",
                        "description": "The console command, e.g. 'tps' or 'worldinfo probe 4 4'",
                    }
                },
                "required": ["cmd"],
            },
        },
        {
            "name": "probe_chunk",
            "description": (
                "Probe the terrain of a chunk in the managed world: surface "
                "heights and the block column at three positions."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "chunk_x": {"type": "integer"},
                    "chunk_z": {"type": "integer"},
                },
                "required": ["chunk_x", "chunk_z"],
            },
        },
        {
            "name": "probe_block",
            "description": "Read the block at world coordinates.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "x": {"type": "integer"},
                    "y": {"type": "integer"},
                    "z": {"type": "integer"},
                },
                "required": ["x", "y", "z"],
            },
        },
        {
            "name": "log_tail",
            "description": "Return the last N lines of the server log.",
            "inputSchema": {
                "type": "object",
                "properties": {"lines": {"type": "integer", "default": 50}},
            },
        },
        {
            "name": "server_status",
            "description": "Report whether the DevMC server is running (RCON reachable).",
            "inputSchema": {"type": "object", "properties": {}},
        },
        {
            "name": "server_start",
            "description": "Start the DevMC server detached and wait until it is ready.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "timeout": {"type": "integer", "default": 180}
                },
            },
        },
        {
            "name": "server_stop",
            "description": "Gracefully stop the DevMC server and wait for shutdown.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "timeout": {"type": "integer", "default": 120}
                },
            },
        },
        {
            "name": "deploy_jar",
            "description": "Copy a built plugin jar into the server's plugins directory.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "source": {
                        "type": "string",
                        "description": "Absolute path to the built jar.",
                    },
                    "name": {
                        "type": "string",
                        "description": "Optional destination filename.",
                    },
                },
                "required": ["source"],
            },
        },
        {
            "name": "restart",
            "description": (
                "Deploy an optional jar, stop the server, start it again, and "
                "wait until ready. The one-call dev loop."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "jar": {
                        "type": "string",
                        "description": "Optional absolute path to a jar to deploy first.",
                    },
                    "name": {"type": "string"},
                    "timeout": {"type": "integer", "default": 180},
                },
            },
        },
        {
            "name": "build",
            "description": (
                "Run a Maven build and return only the result plus error lines "
                "(token-efficient). Use 'all' or omit the module for the reactor."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "module": {
                        "type": "string",
                        "description": "Plugin directory, e.g. plugin-worldgen, or 'all'.",
                    },
                    "clean": {"type": "boolean", "default": False},
                    "skip_tests": {"type": "boolean", "default": True},
                    "install": {
                        "type": "boolean",
                        "default": False,
                        "description": "Use 'install' instead of 'package'.",
                    },
                },
            },
        },
        {
            "name": "rebuild",
            "description": (
                "One-call dev loop: build a module, deploy its jar, restart the "
                "server, and wait until ready. Aborts before deploy on failure."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "module": {"type": "string"},
                    "clean": {"type": "boolean", "default": False},
                    "skip_tests": {"type": "boolean", "default": True},
                    "timeout": {"type": "integer", "default": 180},
                },
                "required": ["module"],
            },
        },
        {
            "name": "log_grep",
            "description": (
                "Search the server log with a regex and return only the matching "
                "lines (last N). Far cheaper than tailing a large log."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "pattern": {"type": "string"},
                    "lines": {"type": "integer", "default": 30},
                    "ignore_case": {"type": "boolean", "default": True},
                },
                "required": ["pattern"],
            },
        },
        {
            "name": "wait_log",
            "description": (
                "Wait until a regex appears in new log output (e.g. a long pregen "
                "or regeneration finishing). Returns the matched line or timeout."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "pattern": {"type": "string"},
                    "timeout": {"type": "integer", "default": 120},
                    "ignore_case": {"type": "boolean", "default": True},
                },
                "required": ["pattern"],
            },
        },
        {
            "name": "command_batch",
            "description": (
                "Run several console commands over one RCON connection and return "
                "combined 'cmd => output' lines."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "cmds": {
                        "type": "array",
                        "items": {"type": "string"},
                    }
                },
                "required": ["cmds"],
            },
        },
        {
            "name": "regen_explode",
            "description": (
                "Spawn a non-player explosion in a managed-world chunk to trigger "
                "the dirty-regeneration path. Returns the explosion line."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "chunk_x": {"type": "integer"},
                    "chunk_z": {"type": "integer"},
                    "power": {"type": "number", "default": 4},
                    "y": {
                        "type": "integer",
                        "description": "Optional explosion Y; defaults to surface+1.",
                    },
                },
                "required": ["chunk_x", "chunk_z"],
            },
        },
        {
            "name": "regen_check",
            "description": (
                "One-shot regeneration diagnostic for a chunk: zone, state, ore "
                "count, and non-air block count."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "chunk_x": {"type": "integer"},
                    "chunk_z": {"type": "integer"},
                },
                "required": ["chunk_x", "chunk_z"],
            },
        },
        {
            "name": "regen_wait",
            "description": (
                "Wait until a chunk's regeneration finishes (Auto-regen or "
                "Dirty-regen log line) or the timeout elapses."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "chunk_x": {"type": "integer"},
                    "chunk_z": {"type": "integer"},
                    "timeout": {"type": "integer", "default": 120},
                },
                "required": ["chunk_x", "chunk_z"],
            },
        },
        {
            "name": "regen_cycle",
            "description": (
                "Override the regeneration cycle for testing (seconds), or pass 0 "
                "to clear it. Pulls already-scheduled chunks in."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {"seconds": {"type": "integer"}},
            },
        },
        {
            "name": "regen_force",
            "description": "Immediately regenerate one chunk, ignoring its schedule.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "chunk_x": {"type": "integer"},
                    "chunk_z": {"type": "integer"},
                },
                "required": ["chunk_x", "chunk_z"],
            },
        },
        {
            "name": "regen_scenario",
            "description": (
                "Full regeneration check in one call: report the chunk before, "
                "explode it, wait for the regen log line, and report it after. "
                "Use to verify terrain AND resources reset."
            ),
            "inputSchema": {
                "type": "object",
                "properties": {
                    "chunk_x": {"type": "integer"},
                    "chunk_z": {"type": "integer"},
                    "power": {"type": "number", "default": 6},
                    "y": {"type": "integer"},
                    "timeout": {"type": "integer", "default": 120},
                },
                "required": ["chunk_x", "chunk_z"],
            },
        },
    ]


def _log_tail(cfg, lines):
    try:
        with open(cfg["log_path"], "r", encoding="utf-8", errors="replace") as f:
            data = f.readlines()
        return "".join(data[-int(lines):])
    except OSError as e:
        return "cannot read log: %s" % e


def call_tool(name, args):
    cfg = read_config()

    # Lifecycle tools must work even when the server is down, so they do not
    # assume an RCON connection.
    if name == "log_tail":
        return _log_tail(cfg, args.get("lines", 50))
    if name == "server_status":
        return _server_status(cfg)
    if name == "deploy_jar":
        return _deploy(cfg, args.get("source"), args.get("name"))
    if name == "server_start":
        if _rcon_ok(cfg):
            return "already running"
        pid = _server_start(cfg)
        ready = _wait_ready(cfg, args.get("timeout", cfg["ready_timeout"]))
        return "started pid=%s ready=%s" % (pid, ready)
    if name == "server_stop":
        stopped = _server_stop(cfg, args.get("timeout", cfg["stop_timeout"]))
        return "stopped=%s" % stopped
    if name == "restart":
        notes = []
        if args.get("jar"):
            notes.append(_deploy(cfg, args["jar"], args.get("name")))
        notes.append("stopped=%s" % _server_stop(cfg, cfg["stop_timeout"]))
        pid = _server_start(cfg)
        ready = _wait_ready(cfg, args.get("timeout", cfg["ready_timeout"]))
        notes.append("started pid=%s ready=%s" % (pid, ready))
        return "; ".join(notes)
    if name == "build":
        return _build(
            cfg,
            args.get("module", "all"),
            args.get("clean", False),
            args.get("skip_tests", True),
            None,
            args.get("install", False),
        )
    if name == "rebuild":
        return _rebuild(
            cfg,
            args.get("module"),
            args.get("clean", False),
            args.get("skip_tests", True),
            args.get("timeout", cfg["ready_timeout"]),
        )
    if name == "log_grep":
        return _log_grep(
            cfg,
            args.get("pattern", ""),
            args.get("lines", 30),
            args.get("ignore_case", True),
        )
    if name == "wait_log":
        return _wait_log(
            cfg,
            args.get("pattern", ""),
            args.get("timeout", 120),
            args.get("ignore_case", True),
        )
    if name == "command_batch":
        return _command_batch(cfg, args.get("cmds", []))
    if name == "regen_wait":
        pattern = r"(Auto-regen|Dirty-regen) complete devmc:%d:%d" % (
            int(args["chunk_x"]),
            int(args["chunk_z"]),
        )
        return _wait_log(cfg, pattern, args.get("timeout", 120), True)
    if name == "regen_scenario":
        cx = int(args["chunk_x"])
        cz = int(args["chunk_z"])
        power = args.get("power", 6)
        rcon = Rcon(cfg)
        rcon.connect()
        try:
            before = rcon.command("worldinfo regencheck %d %d" % (cx, cz))
            cmd = "worldinfo explode %d %d %s" % (cx, cz, power)
            if args.get("y") is not None:
                cmd += " %d" % int(args["y"])
            exploded = rcon.command(cmd)
            state = rcon.command("worldinfo chunk %d %d" % (cx, cz))
        finally:
            rcon.close()
        waited = _wait_log(
            cfg,
            r"(Auto-regen|Dirty-regen) complete devmc:%d:%d" % (cx, cz),
            args.get("timeout", 120),
            True,
        )
        rcon = Rcon(cfg)
        rcon.connect()
        try:
            after = rcon.command("worldinfo regencheck %d %d" % (cx, cz))
        finally:
            rcon.close()
        return "\n".join(
            [
                "BEFORE: " + before,
                "EXPLODE: " + exploded,
                "STATE: " + state,
                "WAIT: " + waited,
                "AFTER: " + after,
            ]
        )

    rcon = Rcon(cfg)
    rcon.connect()
    try:
        if name == "command":
            return rcon.command(args.get("cmd", ""))
        if name == "probe_chunk":
            return rcon.command(
                "worldinfo probe %d %d" % (int(args["chunk_x"]), int(args["chunk_z"]))
            )
        if name == "probe_block":
            return rcon.command(
                "worldinfo block %d %d %d"
                % (int(args["x"]), int(args["y"]), int(args["z"]))
            )
        if name == "regen_explode":
            cmd = "worldinfo explode %d %d %s" % (
                int(args["chunk_x"]),
                int(args["chunk_z"]),
                args.get("power", 4),
            )
            if args.get("y") is not None:
                cmd += " %d" % int(args["y"])
            return rcon.command(cmd)
        if name == "regen_check":
            return rcon.command(
                "worldinfo regencheck %d %d"
                % (int(args["chunk_x"]), int(args["chunk_z"]))
            )
        if name == "regen_cycle":
            return rcon.command("worldinfo cycle %d" % int(args.get("seconds", 0)))
        if name == "regen_force":
            return rcon.command(
                "worldinfo regen %d %d"
                % (int(args["chunk_x"]), int(args["chunk_z"]))
            )
        return "unknown tool %s" % name
    finally:
        rcon.close()


def mcp_main():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except ValueError:
            continue
        method = msg.get("method")
        req_id = msg.get("id")
        if method == "initialize":
            reply = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": MCP_PROTOCOL_VERSION,
                    "capabilities": {"tools": {}},
                    "serverInfo": {"name": "devmc", "version": "1.0.0"},
                },
            }
        elif method == "tools/list":
            reply = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {"tools": tool_specs()},
            }
        elif method == "tools/call":
            params = msg.get("params", {})
            name = params.get("name")
            args = params.get("arguments", {})
            try:
                text = call_tool(name, args)
                result = {
                    "content": [{"type": "text", "text": text}],
                    "isError": False,
                }
            except Exception as e:  # noqa: BLE001
                result = {
                    "content": [{"type": "text", "text": "error: %s" % e}],
                    "isError": True,
                }
            reply = {"jsonrpc": "2.0", "id": req_id, "result": result}
        elif method == "ping":
            reply = {"jsonrpc": "2.0", "id": req_id, "result": {}}
        elif method == "notifications/initialized":
            continue
        else:
            reply = {
                "jsonrpc": "2.0",
                "id": req_id,
                "error": {"code": -32601, "message": "unknown method %s" % method},
            }
        sys.stdout.write(json.dumps(reply) + "\n")
        sys.stdout.flush()


def cli_main():
    args = sys.argv[2:]
    if not args:
        print("usage: server.py --cli <tool> [args...]")
        return
    tool = args[0]
    tool_args = {}
    if tool == "command":
        tool_args["cmd"] = " ".join(args[1:])
    elif tool == "probe_chunk" and len(args) >= 3:
        tool_args = {"chunk_x": int(args[1]), "chunk_z": int(args[2])}
    elif tool == "probe_block" and len(args) >= 4:
        tool_args = {"x": int(args[1]), "y": int(args[2]), "z": int(args[3])}
    elif tool == "log_tail":
        tool_args = {"lines": int(args[1]) if len(args) > 1 else 50}
    elif tool == "deploy_jar" and len(args) >= 2:
        tool_args = {"source": args[1]}
        if len(args) > 2:
            tool_args["name"] = args[2]
    elif tool == "restart":
        if len(args) > 1:
            tool_args["jar"] = args[1]
        if len(args) > 2:
            tool_args["name"] = args[2]
    elif tool == "server_start" and len(args) > 1:
        tool_args = {"timeout": int(args[1])}
    elif tool == "server_stop" and len(args) > 1:
        tool_args = {"timeout": int(args[1])}
    elif tool == "build":
        if len(args) > 1:
            tool_args["module"] = args[1]
        if "--clean" in args:
            tool_args["clean"] = True
        if "--tests" in args:
            tool_args["skip_tests"] = False
    elif tool == "rebuild" and len(args) > 1:
        tool_args["module"] = args[1]
    elif tool == "log_grep" and len(args) > 1:
        tool_args["pattern"] = args[1]
        if len(args) > 2:
            tool_args["lines"] = int(args[2])
    elif tool == "wait_log" and len(args) > 1:
        tool_args["pattern"] = args[1]
        if len(args) > 2:
            tool_args["timeout"] = int(args[2])
    elif tool == "command_batch":
        tool_args["cmds"] = [part for part in " ".join(args[1:]).split(";;") if part]
    print(call_tool(tool, tool_args))


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--cli":
        cli_main()
    else:
        mcp_main()
