"""WebSocket command server for the remote variant.

This process never touches Bluetooth. The Android app owns the BLE link to the watch.
Commands arrive from the app, gshock_api builds the protocol messages, and the raw GATT
writes are relayed to the app. Watch notifications come back from the app and are fed into
gshock_api, which parses them and produces the result that is returned to the app.
"""

from __future__ import annotations

import argparse
import asyncio
import dataclasses
import json
import logging
from collections.abc import Awaitable, Callable
from datetime import datetime
from enum import Enum
from typing import Any

import websockets

from gshock_api.casio_constants import CasioConstants
from gshock_api.gshock_api import GshockAPI
from gshock_api.iolib.dst_watch_state_io import DtsState
from gshock_api.iolib.home_time_io import HomeTimeIO, HomeTimeIOFunctional
from gshock_api.iolib.alarms_io import AlarmsIO
from gshock_api.iolib.events_io import EventsIO
from gshock_api.iolib.settings_io import SettingsIO
from gshock_api.iolib.step_counter_io import StepCounterIO
from gshock_api.iolib.time_adjustment_io import TimeAdjustmentIO
from gshock_api.iolib.world_cities_io import WorldCitiesIO
from gshock_api.message_dispatcher import MessageDispatcher
from gshock_api.utils import to_casio_cmd
from gshock_api.watch_info import watch_info

LOGGER = logging.getLogger("remote_server")
COMMAND_TIMEOUT_SECONDS = 12.0  # below the app's 15 s timeout so the server's error is the one reported
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


# Android Settings (camelCase) <-> gshock_api settings (snake_case) for the watch fields the
# encoder reads. Other Android fields (font, DnD, hourlyChime, ...) have no watch encoding here.
SETTINGS_TO_LIBRARY = {
    "timeFormat": "time_format",
    "buttonTone": "button_tone",
    "autoLight": "auto_light",
    "powerSavingMode": "power_saving_mode",
    "lightDuration": "light_duration",
    "dateFormat": "date_format",
    "language": "language",
}
SETTINGS_FROM_LIBRARY = {
    **{snake: camel for camel, snake in SETTINGS_TO_LIBRARY.items()},
    "time_adjustment": "timeAdjustment",
    "time_adjustment_minutes_after_hour": "adjustmentTimeMinutes",
}


def settings_to_app(settings: Any) -> Any:
    if not isinstance(settings, dict):
        return settings
    return {SETTINGS_FROM_LIBRARY.get(key, key): value for key, value in settings.items()}


def to_library_reminder(reminder: Any) -> dict[str, Any]:
    """Convert an Android Event (flat, camelCase) into the nested shape gshock_api writes."""
    if not isinstance(reminder, dict):
        raise ValueError("set_reminders expects a list of reminder objects")
    start_date = reminder.get("startDate")
    if not isinstance(start_date, dict):
        raise ValueError("each reminder needs a startDate")
    return {
        "title": reminder.get("title", ""),
        "time": {
            "enabled": bool(reminder.get("enabled", False)),
            "repeat_period": reminder.get("repeatPeriod", "NEVER"),
            "start_date": start_date,
            "end_date": reminder.get("endDate") or start_date,
            "days_of_week": reminder.get("daysOfWeek") or None,
        },
    }


def prepare_args(command: str, args: dict[str, Any]) -> dict[str, Any]:
    """Map client argument names and JSON values onto gshock_api parameters."""
    prepared = dict(args)

    if command == "set_alarms":
        alarms = prepared.get("alarms")
        if not isinstance(alarms, list) or not all(isinstance(alarm, dict) for alarm in alarms):
            raise ValueError("set_alarms expects a list of alarm objects")

    if command == "set_time":
        if "time_zone" in prepared:
            prepared["timezone"] = prepared.pop("time_zone")
        if "time_ms" in prepared:
            prepared["current_time"] = datetime.fromtimestamp(int(prepared.pop("time_ms")) / 1000)
        elif isinstance(prepared.get("current_time"), str):
            prepared["current_time"] = datetime.fromisoformat(prepared["current_time"])

    if command == "set_reminders":
        reminders = prepared.pop("reminders", prepared.pop("events", []))
        if not isinstance(reminders, list):
            raise ValueError("set_reminders expects a list of reminder objects")
        prepared = {"events": [to_library_reminder(reminder) for reminder in reminders]}

    if command == "set_timer" and "timer" in prepared:
        prepared["timer_value"] = int(prepared.pop("timer"))

    if command == "get_dst_watch_state" and "state" in prepared:
        state = prepared["state"]
        prepared["state"] = DtsState[state] if isinstance(state, str) else DtsState(int(state))

    return prepared


def to_jsonable(value: Any) -> Any:
    """Convert gshock_api return values into JSON-serializable data."""
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


class ProxyConnection:
    """Stands in for gshock_api's BLE Connection.

    Writes are relayed to the Android app, which performs the GATT write. Notifications
    from the app are passed back into gshock_api's message dispatcher.
    """

    def __init__(self, send_to_app: Callable[[dict[str, Any]], Awaitable[None]]) -> None:
        self._send_to_app = send_to_app

    async def write(self, handle: int, data: bytes | str) -> None:
        payload = to_casio_cmd(data) if isinstance(data, str) else bytes(data)
        await self._send_to_app(
            {"type": "write", "handle": f"0x{handle:02X}", "payload": payload.hex()}
        )

    async def request(self, request: bytes | str) -> None:
        await self.write(0x0C, request)

    async def send_message(self, message: str) -> None:
        await MessageDispatcher.send_to_watch(message)

    @staticmethod
    def on_notification(data: bytes, characteristic_uuid: str | None = None) -> None:
        if not data:
            return
        if _is_drsp_frame(data, characteristic_uuid):
            StepCounterIO.on_drsp_received(data)  # step-read length announcement
            return
        if _is_step_fragment(data, characteristic_uuid):
            StepCounterIO.on_received(data)  # continuation fragment: no key byte, handled here
            return
        try:
            MessageDispatcher.on_received(data)  # keyed frames, including the first step fragment
        except RuntimeError as error:
            # The watch sends unsolicited frames (button presses, app info). gshock_api raises when
            # no request is waiting for one; that must not close the app's connection.
            LOGGER.debug("Ignoring unsolicited watch notification: %s", error)


def _step_read_pending() -> bool:
    return StepCounterIO.result is not None


def _is_drsp_frame(data: bytes, characteristic_uuid: str | None) -> bool:
    if characteristic_uuid is not None:
        return characteristic_uuid == CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID
    return data[:2] == b"\x00\x11"


def _is_step_fragment(data: bytes, characteristic_uuid: str | None) -> bool:
    """Continuation fragments of a step read start with 0x00 and carry no key byte. Without the
    characteristic UUID they are recognized by that and by a step read being pending."""
    if not _step_read_pending() or data[0] != 0x00:
        return False
    if characteristic_uuid is None:
        return True
    return characteristic_uuid == CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID


class RemoteServer:
    def __init__(self) -> None:
        self.active_websocket: Any | None = None
        self.watch_connected = False
        self.lock = asyncio.Lock()
        self.tasks: set[asyncio.Task[None]] = set()
        self.connection = ProxyConnection(self.send_to_app)
        self.api = GshockAPI(self.connection)

    async def send_to_app(self, message: dict[str, Any]) -> None:
        if self.active_websocket is None:
            raise ConnectionError("Android app is not connected")
        await self.active_websocket.send(json.dumps(message))

    async def handle(self, websocket: Any, _path: str = "") -> None:
        self.active_websocket = websocket
        LOGGER.info("App connected: %s", websocket.remote_address)
        try:
            async for raw_message in websocket:
                self.handle_message(websocket, raw_message)
        except websockets.exceptions.ConnectionClosed as error:
            LOGGER.info("App connection closed: %s", error)
        finally:
            self.active_websocket = None
            self.watch_connected = False
            LOGGER.info("App disconnected")

    def handle_message(self, websocket: Any, raw_message: str) -> None:
        try:
            self._dispatch_message(websocket, raw_message)
        except Exception:
            # One bad message must not close the connection to the app.
            LOGGER.exception("Failed to handle message from app")

    def _dispatch_message(self, websocket: Any, raw_message: str) -> None:
        """Dispatches one message. Commands run as tasks so the receive loop keeps reading
        notifications while a command waits for its watch response."""
        LOGGER.debug("App -> server: %s", raw_message)
        try:
            message = json.loads(raw_message)
        except json.JSONDecodeError:
            return
        if not isinstance(message, dict):
            return

        message_type = message.get("type")
        if message_type == "command":
            task = asyncio.create_task(self.handle_command(websocket, message))
            self.tasks.add(task)
            task.add_done_callback(self.tasks.discard)
        elif message_type == "connected":
            self.on_watch_connected(message)
        elif message_type == "disconnected":
            self.watch_connected = False
            LOGGER.info("App reports watch disconnected")
        elif message_type == "notification":
            payload = message.get("payload", "")
            if isinstance(payload, str) and payload:
                self.connection.on_notification(bytes.fromhex(payload), message.get("uuid"))
        elif message_type == "log":
            LOGGER.info("App: %s", message.get("msg", message.get("message", "")))

    def on_watch_connected(self, message: dict[str, Any]) -> None:
        name = message.get("name")
        if isinstance(name, str) and name:
            watch_info.set_name_and_model(name)
        self.watch_connected = True
        LOGGER.info("App reports watch connected: %s", message.get("address", "?"))

    async def handle_command(self, websocket: Any, request: dict[str, Any]) -> None:
        request_id = request.get("id")
        command = request.get("command")
        args = request.get("args") or {}

        if command not in API_COMMANDS:
            await self.send_error(websocket, request_id, f"Unsupported command: {command!r}")
            return
        if not isinstance(args, dict):
            await self.send_error(websocket, request_id, "args must be a JSON object")
            return
        if not self.watch_connected:
            await self.send_error(websocket, request_id, "Watch is not connected to the app")
            return

        LOGGER.info("Executing command %r with args %r", command, args)
        try:
            result = await self.run_command(command, args)
        except Exception as error:
            LOGGER.warning("Command %r failed: %s", command, error, exc_info=True)
            await self.send_error(websocket, request_id, str(error) or type(error).__name__)
            return

        response: dict[str, Any] = {"type": "response", "id": request_id, "ok": True}
        if result is not None:
            response["result"] = result
        await websocket.send(json.dumps(response))

    async def run_command(self, command: str, args: dict[str, Any]) -> Any:
        async with self.lock:
            if not watch_info.name:
                # The model decides which protocol and features gshock_api uses, so read it once.
                name = await asyncio.wait_for(self.api.get_watch_name(), timeout=COMMAND_TIMEOUT_SECONDS)
                if name:
                    watch_info.set_name_and_model(name)
                    LOGGER.info("Watch identified as %s", name)

            if command == "get_step_count" and not watch_info.hasStepCounter:
                # Fail now instead of waiting out the timeout for a reply that never comes.
                raise NotImplementedError(f"{watch_info.name} has no step counter")

            if command == "get_home_time":
                # gshock_api's standard protocol returns the raw frame here; HomeTimeIO decodes it.
                awaitable = HomeTimeIO.request(self.connection, int(args.get("slot", 0)))
            elif command == "get_world_cities":
                # Same frame layout as home time, but the standard protocol returns raw bytes.
                awaitable = self.read_world_city(int(args.get("city_number", 0)))
            elif command == "set_alarms":
                awaitable = self.set_alarms(prepare_args(command, args)["alarms"])
            elif command == "set_reminders":
                awaitable = self.set_reminders(prepare_args(command, args)["events"])
            elif command == "set_settings":
                awaitable = self.set_settings(args.get("settings"))
            elif command in ("get_settings", "get_basic_settings"):
                awaitable = self.read_settings(command)
            else:
                awaitable = getattr(self.api, command)(**prepare_args(command, args))
            result = await asyncio.wait_for(awaitable, timeout=COMMAND_TIMEOUT_SECONDS)
            return to_jsonable(result)

    async def set_alarms(self, alarms: list[dict[str, Any]]) -> None:
        AlarmsIO.connection = self.connection  # normally set by a prior get_alarms
        await self.api.set_alarms(alarms)

    async def set_reminders(self, events: list[dict[str, Any]]) -> None:
        EventsIO.connection = self.connection  # normally set by a prior get_reminders
        await self.api.set_reminders(events)

    async def read_settings(self, command: str) -> dict[str, Any]:
        settings = await getattr(self.api, command)()
        return settings_to_app(settings)

    async def set_settings(self, settings: Any) -> None:
        if not isinstance(settings, dict):
            raise ValueError("set_settings expects a settings object")

        library: dict[str, Any] = {}
        missing: list[str] = []
        for camel, snake in SETTINGS_TO_LIBRARY.items():
            if camel in settings:
                library[snake] = settings[camel]
            else:
                missing.append(snake)
        if missing:
            # The encoder needs every field, so fill the gaps with what the watch reports now.
            current = await self.api.get_basic_settings()
            for snake in missing:
                library[snake] = current[snake]

        SettingsIO.connection = self.connection  # normally set by a prior read
        await self.api.set_settings(library)

        if "timeAdjustment" in settings and "adjustmentTimeMinutes" in settings:
            # Best effort: the settings frame above is already sent, so a failure here is logged
            # rather than reported as a failed settings write.
            try:
                # gshock_api rejects a set until the current value has been read. Its rejection
                # path is broken (it calls a missing ErrorIO.request), so check the read here.
                TimeAdjustmentIO.connection = self.connection
                await self.api.get_time_adjustment()
                if TimeAdjustmentIO.original_value is None:
                    raise RuntimeError("could not read the watch's time adjustment")
                await self.api.set_time_adjustment(
                    bool(settings["timeAdjustment"]), int(settings["adjustmentTimeMinutes"])
                )
            except Exception as error:
                LOGGER.warning("Time adjustment not applied: %s", error)

    async def read_world_city(self, city_number: int) -> str:
        raw = await WorldCitiesIO.request(self.connection, city_number)
        return HomeTimeIOFunctional.parse_home_city(raw)

    @staticmethod
    async def send_error(websocket: Any, request_id: Any, message: str) -> None:
        await websocket.send(
            json.dumps({"type": "response", "id": request_id, "ok": False, "error": message})
        )


async def serve(host: str, port: int) -> None:
    server = RemoteServer()
    # Pings keep the connection open through Cloudflare, which drops idle WebSockets.
    async with websockets.serve(server.handle, host, port, ping_interval=20):
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
