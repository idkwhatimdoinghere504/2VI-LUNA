#!/usr/bin/env python3
"""Read-only, token-paired LAN bridge for selected 2VI AI project folders."""

from __future__ import annotations

import argparse
import hmac
import http.server
import json
import os
import secrets
import socket
import urllib.parse
from pathlib import Path, PurePosixPath

TEXT_EXTENSIONS = {
    ".kt", ".java", ".py", ".js", ".jsx", ".ts", ".tsx", ".html", ".css",
    ".json", ".md", ".txt", ".xml", ".yml", ".yaml", ".gradle", ".kts",
    ".toml", ".sql", ".sh", ".bat", ".ps1", ".c", ".h", ".cpp",
}
BLOCKED_NAMES = {
    ".git", ".gradle", ".idea", ".vscode", ".venv", "venv", "node_modules",
    "build", "dist", "__pycache__", "secrets", "credentials",
}
BLOCKED_FILE_NAMES = {
    ".env", ".env.local", ".env.production", "local.properties", "secrets.properties",
    "gradle.properties", ".npmrc", ".pypirc", ".netrc", "google-services.json",
    "credentials.json", "service-account.json", "keystore.properties",
}
MAX_PREVIEW_BYTES = 256 * 1024
MAX_LIST_ITEMS = 500


def discover_lan_ip() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("192.0.2.1", 1))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


def hidden_or_private(path: Path) -> bool:
    if path.name.lower() in BLOCKED_FILE_NAMES or path.suffix.lower() in {".pem", ".key", ".p12", ".pfx", ".keystore", ".jks"}:
        return True
    return any(part.startswith(".") or part.lower() in BLOCKED_NAMES for part in path.parts)


def parse_skill_header(path: Path) -> dict[str, str] | None:
    try:
        text = path.read_text(encoding="utf-8", errors="replace")[:8192]
    except OSError:
        return None
    metadata: dict[str, str] = {}
    in_header = False
    for line in text.splitlines():
        if line.strip() == "---":
            if not in_header:
                in_header = True
                continue
            break
        if in_header and ":" in line:
            key, value = line.split(":", 1)
            if key.strip() in {"name", "description"}:
                metadata[key.strip()] = value.strip().strip("'\"")
    if not metadata:
        return None
    return {"name": metadata.get("name", path.parent.name), "description": metadata.get("description", "")}


class CompanionHandler(http.server.BaseHTTPRequestHandler):
    server_version = "2VIProjectBridge/1.0"

    def log_message(self, fmt: str, *args: object) -> None:
        print("[request] " + (fmt % args))

    def send_json(self, status: int, value: object) -> None:
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802 - stdlib server hook
        parsed = urllib.parse.urlsplit(self.path)
        if parsed.path == "/health":
            self.send_json(200, {"service": "2VI AI PC Project Bridge", "version": 1})
            return
        expected = "Bearer " + self.server.access_code  # type: ignore[attr-defined]
        supplied = self.headers.get("Authorization", "")
        if not hmac.compare_digest(supplied, expected):
            self.send_json(401, {"error": "Pair this app using the code shown on the PC."})
            return

        query = urllib.parse.parse_qs(parsed.query)
        try:
            if parsed.path == "/api/roots":
                self.send_json(200, {"roots": self.server.root_catalog})  # type: ignore[attr-defined]
            elif parsed.path == "/api/skills":
                self.send_json(200, {"skills": self.list_skills()})
            elif parsed.path == "/api/tree":
                self.send_json(200, self.list_tree(query))
            elif parsed.path == "/api/file":
                self.send_json(200, self.read_file(query))
            else:
                self.send_json(404, {"error": "Unknown endpoint."})
        except (ValueError, OSError) as error:
            self.send_json(400, {"error": str(error)})

    def get_root(self, root_id: str) -> tuple[Path, dict[str, str]]:
        roots = self.server.root_catalog  # type: ignore[attr-defined]
        for root in roots:
            if root["id"] == root_id:
                return Path(root["path"]), root
        raise ValueError("Unknown shared folder.")

    def safe_path(self, root: Path, relative: str) -> Path:
        relative_path = PurePosixPath(relative)
        if relative_path.is_absolute() or any(part in {"..", "."} for part in relative_path.parts):
            raise ValueError("That path is outside the shared folder.")
        current = root
        for part in relative_path.parts:
            current = current / part
            if current.is_symlink():
                raise ValueError("Shortcut and symbolic-link paths are not shared.")
        resolved = current.resolve(strict=True)
        if not resolved.is_relative_to(root):
            raise ValueError("That path is outside the shared folder.")
        if hidden_or_private(resolved.relative_to(root)):
            raise ValueError("That file is private or excluded.")
        return resolved

    def list_tree(self, query: dict[str, list[str]]) -> dict[str, object]:
        root_id = query.get("root", [""])[0]
        relative = query.get("path", [""])[0]
        root, _ = self.get_root(root_id)
        folder = self.safe_path(root, relative) if relative else root
        if not folder.is_dir():
            raise ValueError("That item is not a folder.")
        items: list[dict[str, object]] = []
        for child in sorted(folder.iterdir(), key=lambda item: (not item.is_dir(), item.name.casefold())):
            if child.is_symlink() or hidden_or_private(Path(child.name)):
                continue
            if child.is_dir():
                items.append({"name": child.name, "path": child.relative_to(root).as_posix(), "type": "folder"})
            elif child.is_file() and child.suffix.lower() in TEXT_EXTENSIONS and child.stat().st_size <= MAX_PREVIEW_BYTES:
                items.append({"name": child.name, "path": child.relative_to(root).as_posix(), "type": "file", "bytes": child.stat().st_size})
            if len(items) >= MAX_LIST_ITEMS:
                break
        return {"root": root_id, "path": relative, "items": items, "truncated": len(items) >= MAX_LIST_ITEMS}

    def read_file(self, query: dict[str, list[str]]) -> dict[str, object]:
        root_id = query.get("root", [""])[0]
        relative = query.get("path", [""])[0]
        root, _ = self.get_root(root_id)
        path = self.safe_path(root, relative)
        if not path.is_file() or path.suffix.lower() not in TEXT_EXTENSIONS:
            raise ValueError("Only supported text and source files can be opened.")
        if path.stat().st_size > MAX_PREVIEW_BYTES:
            raise ValueError("That file is too large to preview (limit: 256 KB).")
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError as error:
            raise ValueError("That file is not UTF-8 text and cannot be previewed.") from error
        return {"root": root_id, "path": relative, "name": path.name, "text": text}

    def list_skills(self) -> list[dict[str, str]]:
        skills_root = self.server.skills_root  # type: ignore[attr-defined]
        if skills_root is None:
            return []
        results = []
        for folder in sorted(skills_root.iterdir(), key=lambda item: item.name.casefold()):
            if not folder.is_dir() or folder.is_symlink() or hidden_or_private(Path(folder.name)):
                continue
            skill_file = folder / "SKILL.md"
            if skill_file.is_file():
                metadata = parse_skill_header(skill_file)
                if metadata:
                    results.append({**metadata, "folder": folder.name})
        return results


def main() -> None:
    parser = argparse.ArgumentParser(description="Share selected PC projects with 2VI AI in read-only mode.")
    parser.add_argument("--root", action="append", default=[], help="Project folder to expose; can be provided more than once.")
    parser.add_argument("--skills", default=str(Path.home() / ".agents" / "skills"), help="Folder containing installed SKILL.md files.")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    if args.root:
        root_args = args.root
    else:
        root_args = [str(Path.home() / "Desktop"), str(Path.home() / "Documents" / "1REAL WORK")]
    catalog = []
    for raw_path in root_args:
        path = Path(raw_path).expanduser().resolve(strict=True)
        if not path.is_dir():
            parser.error(f"Shared root is not a folder: {path}")
        catalog.append({"id": str(len(catalog)), "name": path.name or str(path), "path": str(path)})
    if not catalog:
        parser.error("Provide at least one --root folder.")

    skills_root = Path(args.skills).expanduser().resolve()
    if not skills_root.is_dir():
        skills_root = None
    access_code = secrets.token_urlsafe(32)

    server = http.server.ThreadingHTTPServer(("0.0.0.0", args.port), CompanionHandler)
    server.access_code = access_code  # type: ignore[attr-defined]
    server.root_catalog = catalog  # type: ignore[attr-defined]
    server.skills_root = skills_root  # type: ignore[attr-defined]
    print("2VI AI PC Project Bridge (read-only)")
    print(f"PC address: http://{discover_lan_ip()}:{args.port}")
    print(f"Pairing code: {access_code}")
    print("Use the Android app and PC on the same trusted Wi-Fi. Press Ctrl+C to stop sharing.")
    print("Shared folders:")
    for root in catalog:
        print(f"  - {root['path']}")
    if skills_root:
        print(f"Skill descriptions: {skills_root}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nProject sharing stopped.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
