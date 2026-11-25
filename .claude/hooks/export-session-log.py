#!/usr/bin/env python3
"""Hook Claude Code (Stop / SessionEnd): zapisuje log sesji AI do ai-logs/.

Wejście (stdin, JSON od Claude Code): session_id, transcript_path, ...
Wyjście:
  ai-logs/<data>_<session>.jsonl  - wiadomości user/assistant (surowe, przefiltrowane)
  ai-logs/<data>_<session>.md     - czytelna wersja: prompty, odpowiedzi, wywołania narzędzi
  ai-logs/raw/<data>_<session>*.jsonl - pełny surowy transkrypt (+ subagenci), bez cięcia

Plik jest nadpisywany przy każdym zdarzeniu, więc zawsze odzwierciedla
aktualny stan sesji. Adresy e-mail (oraz sam login autora z git config) są maskowane.
"""
import glob
import json
import os
import re
import subprocess
import sys
from datetime import datetime

EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
MAX_TOOL_OUTPUT = 2000


REDACT_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "redact.local")


def _local_parts():
    """Lokalne części adresów do maskowania także bez domeny.

    Źródła: git config user.email, zmienne GIT_AUTHOR_EMAIL/EMAIL oraz
    nieśledzony plik .claude/redact.local (jeden adres na linię).
    """
    parts = set()
    if os.path.isfile(REDACT_FILE):
        with open(REDACT_FILE, encoding="utf-8") as f:
            parts.update(l.strip() for l in f if l.strip() and not l.startswith("#"))
    for env in ("GIT_AUTHOR_EMAIL", "EMAIL"):
        if os.environ.get(env):
            parts.add(os.environ[env])
    try:
        out = subprocess.run(["git", "config", "user.email"], capture_output=True, text=True, timeout=5).stdout.strip()
        if out:
            parts.add(out)
    except Exception:
        pass
    return [p.split("@")[0] for p in parts if "@" in p and len(p.split("@")[0]) >= 4]


LOCAL_RE = [re.compile(re.escape(p), re.I) for p in _local_parts()]


def redact(text: str) -> str:
    text = EMAIL_RE.sub("<email>", text)
    for r in LOCAL_RE:
        text = r.sub("<email>", text)
    return text


def write_raw(src: str, dest: str) -> None:
    """Pełny, nieprzycięty transkrypt JSONL z zamaskowanymi adresami e-mail."""
    with open(src, encoding="utf-8") as fin, open(dest, "w", encoding="utf-8") as fout:
        for line in fin:
            fout.write(redact(line))


def clip(text: str, limit: int = MAX_TOOL_OUTPUT) -> str:
    return text if len(text) <= limit else text[:limit] + f"\n… [obcięto {len(text) - limit} znaków]"


def block_text(content) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "\n".join(block_text(c.get("text", c.get("content", ""))) if isinstance(c, dict) else str(c) for c in content)
    return str(content)


def strip_reminders(text: str) -> str:
    return re.sub(r"<system-reminder>.*?</system-reminder>", "", text, flags=re.S).strip()


def render(entries) -> str:
    out = []
    for e in entries:
        msg = e["message"]
        ts = e.get("timestamp", "")[:19].replace("T", " ")
        content = msg.get("content")
        if e["type"] == "user":
            if isinstance(content, str):
                text = strip_reminders(content)
                if text:
                    out.append(f"## 👤 Użytkownik  \n<sub>{ts}</sub>\n\n{text}\n")
                continue
            for b in content:
                if b.get("type") == "text":
                    text = strip_reminders(b.get("text", ""))
                    if text:
                        out.append(f"## 👤 Użytkownik  \n<sub>{ts}</sub>\n\n{text}\n")
                elif b.get("type") == "tool_result":
                    res = clip(strip_reminders(block_text(b.get("content", ""))))
                    if res:
                        out.append(f"<details><summary>wynik narzędzia</summary>\n\n```\n{res}\n```\n</details>\n")
        else:
            for b in content or []:
                t = b.get("type")
                if t == "text" and b.get("text", "").strip():
                    out.append(f"## 🤖 Claude  \n<sub>{ts}</sub>\n\n{b['text'].strip()}\n")
                elif t == "tool_use":
                    inp = json.dumps(b.get("input", {}), ensure_ascii=False, indent=2)
                    out.append(f"**🔧 {b.get('name')}**\n\n```json\n{clip(inp)}\n```\n")
    return "\n".join(out)


def main():
    payload = json.load(sys.stdin)
    src = payload.get("transcript_path")
    if not src or not os.path.isfile(src):
        return
    project = os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or os.getcwd()
    out_dir = os.path.join(project, "ai-logs")
    os.makedirs(out_dir, exist_ok=True)

    entries, first_ts = [], None
    with open(src, encoding="utf-8") as f:
        for line in f:
            try:
                e = json.loads(line)
            except json.JSONDecodeError:
                continue
            if e.get("type") in ("user", "assistant") and isinstance(e.get("message"), dict) and not e.get("isSidechain"):
                entries.append(e)
                first_ts = first_ts or e.get("timestamp")
    if not entries:
        return

    session = payload.get("session_id") or os.path.splitext(os.path.basename(src))[0]
    day = (first_ts or datetime.now().isoformat())[:10]
    base = os.path.join(out_dir, f"{day}_{session[:8]}")

    raw_dir = os.path.join(out_dir, "raw")
    os.makedirs(raw_dir, exist_ok=True)
    write_raw(src, os.path.join(raw_dir, f"{day}_{session[:8]}.jsonl"))
    sub_dir = os.path.join(os.path.dirname(src), session, "subagents")
    for sub in sorted(glob.glob(os.path.join(sub_dir, "*.jsonl"))):
        write_raw(sub, os.path.join(raw_dir, f"{day}_{session[:8]}_{os.path.basename(sub)}"))

    with open(base + ".jsonl", "w", encoding="utf-8") as f:
        for e in entries:
            slim = {k: e.get(k) for k in ("type", "timestamp", "uuid", "parentUuid", "message")}
            f.write(redact(json.dumps(slim, ensure_ascii=False)) + "\n")

    header = f"# Log sesji AI (Claude Code)\n\n- Sesja: `{session}`\n- Start: {first_ts}\n- Ostatni zapis: {datetime.now().isoformat(timespec='seconds')}\n\n---\n\n"
    with open(base + ".md", "w", encoding="utf-8") as f:
        f.write(redact(header + render(entries)))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # hook nie może blokować pracy
        print(f"export-session-log: {exc}", file=sys.stderr)
