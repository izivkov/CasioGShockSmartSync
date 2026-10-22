"""WebSocket command server for remote variant (WebSocket communication only, BLE handled by Android client)."""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
from typing import Any

import websockets

LOGGER = logging.getLogger("remote_server")
API_COMMANDS = frozenset(
    {
        "get_watch_name",
        "get_pressed_button",
        "get_world_cities",
        "get_dst_for_world_cities",
        "get_dst_watch_state",
        "get_home_time",
        "set_time",
        "get_alarms",
        "set_alarms",
        "get_timer",
        "set_timer",
        "get_watch_condition",
        "get_time_adjustment",
        "set_time_adjustment",
        "get_basic_settings",
        "get_settings",
        "set_settings",
        "get_step_count",
        "get_reminders",
        "get_event_from_watch",
        "set_reminders",
        "get_app_info",
        "send_app_notification",
    }
)


class RemoteServer:
    def __init__(self) -> None:
        self.active_websocket: Any | None = None

    async def handle(self, websocket: Any, _path: str = "") -> None:
        self.active_websocket = websocket
        LOGGER.info("Client connected: %s", websocket.remote_address)
        try:
            async for raw_message in websocket:
                await self.handle_message(websocket, raw_message)
        except websockets.exceptions.ConnectionClosed as error:
            LOGGER.info("Client connection closed: %s", error)
        finally:
            self.active_websocket = None
            LOGGER.info("Client disconnected")

    async def handle_message(self, websocket: Any, raw_message: str) -> None:
        LOGGER.info("WebSocket <- client: %s", raw_message)
        try:
            request = json.loads(raw_message)
            if not isinstance(request, dict):
                return
        except json.JSONDecodeError:
            return

        message_type = request.get("type")
        if message_type == "command":
            request_id = request.get("id")
            command = request.get("command")
            args = request.get("args", {})
            LOGGER.info("Executing command %r with args %r", command, args)

            result = self.get_result(command, args)
            response = {"type": "response", "id": request_id, "ok": True}
            if result is not None:
                response["result"] = result

            await websocket.send(json.dumps(response))
        elif message_type == "connected":
            LOGGER.info("Android reports watch connected")
        elif message_type == "log":
            LOGGER.info("Android client: %s", request.get("msg", request.get("message", "")))

    def get_result(self, command: str, args: dict[str, Any]) -> Any:
        if command == "get_watch_name":
            return "CASIO G-SHOCK (Remote)"
        if command == "get_timer":
            return 300
        if command == "get_home_time":
            return "UTC"
        if command == "get_watch_condition":
            return {"battery": 100, "temperature": 22}
        if command == "get_alarms":
            return []
        if command == "get_settings":
            return {"hourlyChime": True, "lightDuration": 2}
        if command == "get_app_info":
            return "GShockRemote Bridge 1.0"
        return None


async def serve(host: str, port: int) -> None:
    server = RemoteServer()
    async with websockets.serve(server.handle, host, port, ping_interval=None):
        LOGGER.info("Remote command server listening on ws://%s:%d", host, port)
        await asyncio.Future()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9999)
    options = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        asyncio.run(serve(options.host, options.port))
    except KeyboardInterrupt:
        LOGGER.info("Server stopped")


if __name__ == "__main__":
    main()
