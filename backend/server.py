"""
CommLink PTT WebSocket backend — echo server for local development.
Run: python server.py
Requires: pip install websockets

Android emulator reaches this host at ws://10.0.2.2:8765
Real device on same WiFi: ws://<your-LAN-IP>:8765
"""

import asyncio
import json
import websockets
from datetime import datetime


async def handler(websocket):
    client = websocket.remote_address
    print(f"[{_now()}] Client connected: {client}")

    try:
        async for message in websocket:
            if isinstance(message, bytes):
                # Binary frame = audio chunk
                print(f"[{_now()}] Audio chunk received: {len(message)} bytes")
                await websocket.send("successfully received msg")
            else:
                # Text frame = JSON control message
                print(f"[{_now()}] Message received: {message}")
                try:
                    payload = json.loads(message)
                    msg_type = payload.get("type", "UNKNOWN")
                    print(f"[{_now()}] Type={msg_type}, sender={payload.get('senderName', '?')}")
                except json.JSONDecodeError:
                    pass
                await websocket.send("successfully received msg")

    except websockets.exceptions.ConnectionClosedOK:
        print(f"[{_now()}] Client disconnected cleanly: {client}")
    except websockets.exceptions.ConnectionClosedError as e:
        print(f"[{_now()}] Client disconnected with error: {e}")


def _now():
    return datetime.now().strftime("%H:%M:%S")


async def main():
    host, port = "0.0.0.0", 8765
    print(f"CommLink WebSocket server listening on ws://{host}:{port}")
    print("Android emulator URL : ws://10.0.2.2:8765")
    print("Press Ctrl+C to stop.\n")

    async with websockets.serve(handler, host, port):
        await asyncio.Future()  # run until cancelled


if __name__ == "__main__":
    asyncio.run(main())
