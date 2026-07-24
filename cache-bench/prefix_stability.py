#!/usr/bin/env python3
"""
prefix_stability.py — measure how "prompt-cache friendly" a jar's requests are.

Runs a jar N times through capture_proxy, captures the main agent request each
time, and computes the byte-identical shared prefix across runs. A longer shared
prefix == more of the prompt a provider can serve from its prefix cache.

Usage (prefer the wrapper which sources ~/.config/sai/.env):
    ./run_capture.sh <jar> [runs] [port]

Direct:
    OPENAI_ENDPOINT=... OPENAI_API_KEY=... python3 prefix_stability.py <jar> [runs] [port]
"""
import json, os, sys, subprocess, time, tempfile, signal

JAR    = sys.argv[1]
RUNS   = int(sys.argv[2]) if len(sys.argv) > 2 else 3
PORT   = int(sys.argv[3]) if len(sys.argv) > 3 else 8791
PROMPT = b"hello there\n"          # trailing newline: sai reads one line from stdin
HERE   = os.path.dirname(os.path.abspath(__file__))


def is_main_agent_req(o):
    msgs = o.get("messages", [])
    return bool(msgs) and isinstance(msgs[0].get("content"), str) \
        and "currentTime" in msgs[0]["content"]


def first_main_req(path):
    """First main-agent request in the capture file (tolerant of partial lines)."""
    try:
        lines = open(path).readlines()
    except Exception:
        return None
    for line in lines:
        line = line.strip()
        if not line:
            continue
        try:
            o = json.loads(line)
        except Exception:
            continue
        if is_main_agent_req(o):
            return o
    return None


def capture_one(jar, out, env):
    """Launch the jar, wait until the first main request lands, then kill it."""
    open(out, "w").close()
    proc = subprocess.Popen(
        ["java", "-jar", jar, "--headless", "--model", "openai/cap"],
        stdin=subprocess.PIPE, env=env,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        proc.stdin.write(PROMPT)
        proc.stdin.flush()
        proc.stdin.close()
    except Exception:
        pass
    req = None
    deadline = time.time() + 90
    while time.time() < deadline:
        req = first_main_req(out)
        if req is not None:
            break
        if proc.poll() is not None:
            req = first_main_req(out)
            break
        time.sleep(0.3)
    proc.kill()
    try:
        proc.wait(timeout=5)
    except Exception:
        pass
    return req


def main():
    out = tempfile.NamedTemporaryFile(delete=False, suffix=".jsonl").name
    proxy = subprocess.Popen([sys.executable, f"{HERE}/capture_proxy.py", out, str(PORT)])
    time.sleep(1)
    env = dict(os.environ,
               OPENAI_ENDPOINT=f"http://127.0.0.1:{PORT}",
               OPENAI_API_KEY="dummy")
    prefixes = []
    try:
        for _ in range(RUNS):
            req = capture_one(JAR, out, env)
            if req:
                prefixes.append(req["messages"][0]["content"])
    finally:
        proxy.send_signal(signal.SIGTERM)
        try:
            os.unlink(out)
        except Exception:
            pass

    if len(prefixes) < 2:
        print("!! could not capture >=2 main requests (auth/model issue?)")
        return 1

    base = prefixes[0]
    shared = len(base)
    for p in prefixes[1:]:
        n = min(shared, len(p)); i = 0
        while i < n and base[i] == p[i]:
            i += 1
        shared = min(shared, i)
    pct = shared * 100.0 / len(base)
    print(f"jar            : {os.path.basename(os.path.dirname(JAR)) or JAR}")
    print(f"runs captured  : {len(prefixes)}")
    print(f"prompt bytes   : {len(base)}")
    print(f"shared prefix  : {shared} bytes  ({pct:.1f}%)")
    print(f"first divergence:")
    print(f"   run0: {base[shared:shared+70]!r}")
    print(f"   run1: {prefixes[1][shared:shared+70]!r}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
