#!/usr/bin/env python3
"""Build a Docker context through the local Engine socket.

The factory agent image intentionally has no Docker CLI, while the agentworker
does provide the Docker Engine socket. This small stdlib-only client keeps the
image build revision-bound and executable by the verification harness.
"""

from __future__ import annotations

import argparse
import http.client
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
from urllib.parse import urlencode


DEFAULT_SOCKET = "/var/run/docker.sock"


class UnixSocketConnection(http.client.HTTPConnection):
    def __init__(self, socket_path: str) -> None:
        super().__init__("localhost")
        self.socket_path = socket_path

    def connect(self) -> None:
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.connect(self.socket_path)


def _create_context_archive(context: Path, destination: Path) -> None:
    command = ["tar", "-C", str(context)]
    dockerignore = context / ".dockerignore"
    if dockerignore.is_file():
        command.extend(["--exclude-from", str(dockerignore.resolve())])
    command.extend(["-cf", str(destination), "."])
    subprocess.run(command, check=True)


def _build_path(dockerfile: str, build_args: dict[str, str]) -> str:
    query: dict[str, str] = {
        "dockerfile": dockerfile,
        "rm": "1",
        "forcerm": "1",
    }
    if build_args:
        query["buildargs"] = json.dumps(build_args, separators=(",", ":"))
    return f"/v1.40/build?{urlencode(query)}"


def _parse_build_args(values: list[str]) -> dict[str, str]:
    result: dict[str, str] = {}
    for value in values:
        key, separator, argument = value.partition("=")
        if not separator or not key:
            raise ValueError(f"Ongeldig build-arg (verwacht NAAM=WAARDE): {value}")
        result[key] = argument
    return result


def _stream_build_response(response: http.client.HTTPResponse) -> bool:
    succeeded = True
    while line := response.readline():
        event = json.loads(line)
        if "stream" in event:
            print(event["stream"], end="", flush=True)
        elif "status" in event:
            detail = f" {event['progress']}" if event.get("progress") else ""
            print(f"{event['status']}{detail}", flush=True)
        if "error" in event or "errorDetail" in event:
            message = event.get("error") or event.get("errorDetail", {}).get(
                "message", "Docker-build mislukt"
            )
            print(message, file=sys.stderr, flush=True)
            succeeded = False
    return succeeded


def build(
    *, context: Path, dockerfile: str, build_args: dict[str, str], socket_path: str
) -> int:
    if not context.is_dir():
        print(f"Docker-context bestaat niet: {context}", file=sys.stderr)
        return 2
    if not (context / dockerfile).is_file():
        print(f"Dockerfile bestaat niet in context: {dockerfile}", file=sys.stderr)
        return 2
    if not Path(socket_path).exists():
        print(f"Docker Engine-socket ontbreekt: {socket_path}", file=sys.stderr)
        return 2

    with tempfile.TemporaryDirectory(prefix="factory-docker-build-") as temp_dir:
        archive = Path(temp_dir) / "context.tar"
        _create_context_archive(context, archive)
        connection = UnixSocketConnection(socket_path)
        try:
            with archive.open("rb") as body:
                connection.request(
                    "POST",
                    _build_path(dockerfile, build_args),
                    body=body,
                    headers={
                        "Content-Type": "application/x-tar",
                        "Content-Length": str(archive.stat().st_size),
                    },
                )
            response = connection.getresponse()
            if response.status != 200:
                message = response.read().decode("utf-8", errors="replace")
                print(
                    f"Docker Engine antwoordde met HTTP {response.status}: {message}",
                    file=sys.stderr,
                )
                return 1
            return 0 if _stream_build_response(response) else 1
        except (OSError, http.client.HTTPException, json.JSONDecodeError) as error:
            print(f"Docker-buildbewijs kon niet worden uitgevoerd: {error}", file=sys.stderr)
            return 1
        finally:
            connection.close()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--context", required=True, type=Path)
    parser.add_argument("--dockerfile", default="Dockerfile")
    parser.add_argument("--build-arg", action="append", default=[])
    parser.add_argument("--socket", default=DEFAULT_SOCKET)
    arguments = parser.parse_args()
    try:
        build_args = _parse_build_args(arguments.build_arg)
    except ValueError as error:
        parser.error(str(error))
    return build(
        context=arguments.context.resolve(),
        dockerfile=arguments.dockerfile,
        build_args=build_args,
        socket_path=arguments.socket,
    )


if __name__ == "__main__":
    raise SystemExit(main())
