"""Remote command server.

The Android app is a BLE pipe: it performs the GATT writes this server asks for and sends back
every watch notification with its characteristic UUID. All protocol logic runs here, in gshock_api,
which talks to the watch through ProxyConnection. The app can also request any GshockAPI method by
name; that call runs here and its watch traffic goes through the same pipe.
"""

from __future__ import annotations

import argparse
import asyncio
import dataclasses
import importlib
import inspect
import json
import logging
import os
import pkgutil
import secrets
import sys
from datetime import datetime
from enum import Enum
from http import HTTPStatus
from typing import Any, Callable

import websockets

import gshock_api.iolib as iolib
from gshock_api.casio_constants import CasioConstants
from gshock_api.gshock_api import GshockAPI
from gshock_api.iolib.step_counter_io import StepCounterIO
from gshock_api.message_dispatcher import MessageDispatcher
from gshock_api.utils import to_casio_cmd
from gshock_api.watch_info import watch_info

LOGGER = logging.getLogger("remote_server")
API_TIMEOUT_SECONDS = 30.0
DRSP_UUID = CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID
CONVOY_UUID = CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID


def bind_io_connections(connection: "ProxyConnection") -> None:
    """gshock_api's IO classes need the connection before a write, normally set by an earlier read.
    Bind every IO class to the proxy so any call works regardless of what ran before it."""
    for info in pkgutil.iter_modules(iolib.__path__):
        module = importlib.import_module(f"gshock_api.iolib.{info.name}")
        for _, cls in inspect.getmembers(module, inspect.isclass):
            if cls.__module__ == module.__name__ and "connection" in vars(cls):
                cls.connection = connection


def to_jsonable(value: Any) -> Any:
    if isinstance(value, Enum):
        return value.name
    if value is None or isinstance(value, (str, bool, int, float)):
        return value
    if isinstance(value, (bytes, bytearray)):
        return bytes(value).hex()
    if isinstance(value, datetime):
        return value.isoformat()
    if dataclasses.is_dataclass(value) and not isinstance(value, type):
        return to_jsonable(dataclasses.asdict(value))
    if isinstance(value, dict):
        return {str(key): to_jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple, set)):
        return [to_jsonable(item) for item in value]
    return str(value)


def is_api_method(name: Any) -> bool:
    """Only public coroutine methods of GshockAPI can be called by name."""
    if not isinstance(name, str) or name.startswith("_"):
        return False
    return inspect.iscoroutinefunction(getattr(GshockAPI, name, None))


class ProxyConnection:
    """Stands in for gshock_api's BLE connection. Every write goes to the phone; notifications
    from the phone go back into gshock_api."""

    def __init__(self, send_to_phone: Callable[[dict[str, Any]], Any]) -> None:
        self._send_to_phone = send_to_phone

    async def write(self, handle: int, data: bytes | str) -> None:
        payload = to_casio_cmd(data) if isinstance(data, str) else bytes(data)
        await self._send_to_phone({"type": "write", "handle": f"0x{handle:02X}", "payload": payload.hex()})

    async def request(self, request: bytes | str) -> None:
        await self.write(0x0C, request)

    async def send_message(self, message: str) -> None:
        await MessageDispatcher.send_to_watch(message)

    @staticmethod
    def on_notification(data: bytes, characteristic_uuid: str | None) -> None:
        # Mirrors gshock_api's own notification handling. Step-counter fragments on the Convoy
        # characteristic can start with 0x00 and carry no key, so they are routed by UUID.
        if characteristic_uuid == DRSP_UUID:
            StepCounterIO.on_drsp_received(data)
            return
        if characteristic_uuid == CONVOY_UUID and data[:1] == b"\x00":
            StepCounterIO.on_received(data)
            return
        try:
            MessageDispatcher.on_received(data)
        except RuntimeError as error:
            # Unsolicited watch frames (button presses, app info) raise when nothing is waiting.
            LOGGER.debug("Ignoring unsolicited watch notification: %s", error)


class RemoteServer:
    def __init__(self) -> None:
        self.phone: Any | None = None
        self.watch_ready = False
        self.lock = asyncio.Lock()
        self.tasks: set[asyncio.Task[None]] = set()
        self.connection = ProxyConnection(self.send_to_phone)
        self.api = GshockAPI(self.connection)
        bind_io_connections(self.connection)

    async def send_to_phone(self, message: dict[str, Any]) -> None:
        if self.phone is None:
            raise ConnectionError("Android app is not connected")
        await self.phone.send(json.dumps(message))

    async def handle(self, websocket: Any, _path: str = "") -> None:
        self.phone = websocket
        LOGGER.info("App connected: %s", websocket.remote_address)
        try:
            async for raw_message in websocket:
                self.handle_message(websocket, raw_message)
        except websockets.exceptions.ConnectionClosed as error:
            LOGGER.info("App connection closed: %s", error)
        finally:
            self.phone = None
            self.watch_ready = False
            LOGGER.info("App disconnected")

    def handle_message(self, websocket: Any, raw_message: str) -> None:
        try:
            self._dispatch(websocket, raw_message)
        except Exception:
            LOGGER.exception("Failed to handle message from app")

    def _dispatch(self, websocket: Any, raw_message: str) -> None:
        try:
            message = json.loads(raw_message)
        except json.JSONDecodeError:
            return
        if not isinstance(message, dict):
            return

        message_type = message.get("type")
        if message_type == "api":
            # Api calls wait for watch replies, so they run as tasks and the receive loop keeps reading.
            task = asyncio.create_task(self.handle_api(websocket, message))
            self.tasks.add(task)
            task.add_done_callback(self.tasks.discard)
        elif message_type == "connected":
            self.on_watch_connected(message)
        elif message_type == "disconnected":
            self.watch_ready = False
            LOGGER.info("App reports watch disconnected")
        elif message_type == "notification":
            payload = message.get("payload", "")
            if isinstance(payload, str) and payload:
                self.connection.on_notification(bytes.fromhex(payload), message.get("uuid"))
        elif message_type == "log":
            LOGGER.info("App: %s", message.get("msg", message.get("message", "")))

    def on_watch_connected(self, message: dict[str, Any]) -> None:
        # The app sends the watch's name and address, either as separate fields or as a JSON string.
        address = message.get("address", "")
        name = message.get("name", "")
        if isinstance(address, str) and address.startswith("{"):
            device = json.loads(address)
            name, address = device.get("name", name), device.get("address", "")
        if name:
            watch_info.set_name_and_model(name)
        self.watch_ready = True
        LOGGER.info("App reports watch connected: %s %s", name, address)

    async def handle_api(self, websocket: Any, request: dict[str, Any]) -> None:
        request_id = request.get("id")
        method = request.get("method")
        args = request.get("args") or {}

        if not is_api_method(method):
            await self.send_result(websocket, request_id, False, error=f"Unsupported method: {method!r}")
            return
        if not isinstance(args, dict):
            await self.send_result(websocket, request_id, False, error="args must be a JSON object")
            return
        if not self.watch_ready:
            await self.send_result(websocket, request_id, False, error="Watch is not connected to the app")
            return

        LOGGER.info("API %s(%r)", method, args)
        try:
            async with self.lock:
                result = await asyncio.wait_for(getattr(self.api, method)(**args), timeout=API_TIMEOUT_SECONDS)
        except Exception as error:
            LOGGER.warning("API %s failed: %s", method, error, exc_info=True)
            await self.send_result(websocket, request_id, False, error=str(error) or type(error).__name__)
            return

        await self.send_result(websocket, request_id, True, result=to_jsonable(result))

    @staticmethod
    async def send_result(websocket: Any, request_id: Any, ok: bool, result: Any = None, error: str = "") -> None:
        response: dict[str, Any] = {"type": "api_result", "id": request_id, "ok": ok}
        if ok:
            response["result"] = result
        else:
            response["error"] = error
        await websocket.send(json.dumps(response))


def make_token_check(token: str) -> Callable[[str, Any], Any]:
    """Reject WebSocket handshakes that don't carry 'Authorization: Bearer <token>'."""
    expected = f"Bearer {token}"

    def check(path: str, request_headers: Any) -> Any:
        provided = request_headers.get("Authorization", "")
        if not secrets.compare_digest(provided, expected):
            LOGGER.warning("Rejected connection without a valid token")
            return HTTPStatus.UNAUTHORIZED, [], b"unauthorized\n"
        return None

    return check


async def serve(host: str, port: int, token: str | None) -> None:
    server = RemoteServer()
    # Pings keep the connection open through Cloudflare, which drops idle WebSockets.
    async with websockets.serve(
        server.handle,
        host,
        port,
        ping_interval=20,
        process_request=make_token_check(token) if token else None,
    ):
        if not token:
            LOGGER.warning("Token check DISABLED (--no-token). Local testing only.")
        LOGGER.info("Remote command server listening on ws://%s:%d", host, port)
        await asyncio.Future()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9998)
    parser.add_argument(
        "--no-token",
        action="store_true",
        help="Local testing only: skip the token check. Refused unless --host is 127.0.0.1.",
    )
    options = parser.parse_args()

    if options.no_token:
        if options.host not in ("127.0.0.1", "localhost"):
            sys.exit("--no-token is only allowed with --host 127.0.0.1")
        token = None
    else:
        token = os.environ.get("GSHOCK_REMOTE_TOKEN", "")
        if not token:
            sys.exit("Set GSHOCK_REMOTE_TOKEN to the shared token the app sends in its Authorization header, "
                     "or use --no-token for local testing.")

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        asyncio.run(serve(options.host, options.port, token))
    except KeyboardInterrupt:
        LOGGER.info("Server stopped")


if __name__ == "__main__":
    main()
