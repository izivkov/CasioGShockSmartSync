# Remote Server

`remote_server.py` accepts WebSocket command requests and executes allowlisted
operations through the `gshock_api` library's direct BLE connection. Unlike the
G-Shock Remote Debug server, it does not send BLE write messages to the Android
client.

## Setup

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
python remote_server.py --host 0.0.0.0 --port 9999
```

Omit `--host 0.0.0.0` to bind to localhost only. Use `--watch-address ADDRESS`
to select a paired watch; otherwise the server scans when it receives its first
command. The server host needs Bluetooth LE access.

## Request and response

Send one JSON object per WebSocket message:

```json
{"type":"command","id":"request-1","command":"get_watch_name","args":{}}
```

The server replies with the same request ID:

```json
{"type":"response","id":"request-1","ok":true,"result":"G-SHOCK"}
```

Failures use `"ok":false` and an `error` string. `args` contains keyword
arguments for the named `gshock_api.GshockAPI` operation. Supported commands are
the API methods listed in `API_COMMANDS` in `remote_server.py`.