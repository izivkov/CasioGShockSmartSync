#!/bin/bash
set -e

REMOTE_USER="ivo"
REMOTE_HOST="192.168.1.100"
REMOTE_DIR="~/remote"
LOCAL_SCRIPT="remote_server.py"
LOCAL_PROJECT="deploy/pyproject.toml"

if [ ! -f "$LOCAL_SCRIPT" ]; then
    echo "Error: $LOCAL_SCRIPT not found in the current directory."
    exit 1
fi
if [ ! -f "$LOCAL_PROJECT" ]; then
    echo "Error: $LOCAL_PROJECT not found."
    exit 1
fi

echo "==> Creating remote directory ${REMOTE_DIR} on ${REMOTE_HOST}..."
ssh "${REMOTE_USER}@${REMOTE_HOST}" "mkdir -p ${REMOTE_DIR}"

echo "==> Copying ${LOCAL_SCRIPT} and project file to remote host..."
scp "${LOCAL_SCRIPT}" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/"
scp "${LOCAL_PROJECT}" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/pyproject.toml"

echo "==> Configuring systemd user service for startup persistence..."
ssh "${REMOTE_USER}@${REMOTE_HOST}" << 'REMOTE_EOF'
SERVICE_DIR="$HOME/.config/systemd/user"
SERVICE_PATH="$SERVICE_DIR/gshock-remote-server.service"

mkdir -p "$SERVICE_DIR"

cat << 'SERVICE_EOF' > "$SERVICE_PATH"
[Unit]
Description=G-Shock Remote WebSocket Server
After=network.target

[Service]
Type=simple
WorkingDirectory=/home/ivo/remote
# Bound to localhost: only the Cloudflare Tunnel (cloudflared) on this machine connects to it.
ExecStart=/home/ivo/.local/bin/uv run /home/ivo/remote/remote_server.py --host 127.0.0.1 --port 9998
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
SERVICE_EOF

# Keep user services running without an active SSH login
loginctl enable-linger ivo

systemctl --user daemon-reload
systemctl --user enable gshock-remote-server.service
systemctl --user restart gshock-remote-server.service

echo "==> Remote service status:"
systemctl --user status gshock-remote-server.service --no-pager
REMOTE_EOF

echo "==> Deployment and startup configuration completed successfully!"
