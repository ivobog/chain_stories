import type { RealtimeEvent } from "./types";

type EventHandler = (event: RealtimeEvent) => void;
type ErrorHandler = (error: Error) => void;

export interface RealtimeConnectionOptions {
  baseUrl: string;
  accessToken: string;
  onRoomEvent?: EventHandler;
  onUserEvent?: EventHandler;
  onError?: ErrorHandler;
}

export class RealtimeConnection {
  private socket?: WebSocket;
  private nextSubscriptionId = 1;
  private connected = false;

  constructor(private readonly options: RealtimeConnectionOptions) {}

  connect() {
    if (this.socket && this.socket.readyState !== WebSocket.CLOSED) {
      return;
    }

    const socket = new WebSocket(this.wsUrl("/ws/game"));
    this.socket = socket;
    socket.onopen = () => {
      this.sendFrame("CONNECT", {
        Authorization: `Bearer ${this.options.accessToken}`,
        "accept-version": "1.2",
        "heart-beat": "0,0",
      });
    };
    socket.onmessage = (message) => this.handleMessage(String(message.data));
    socket.onerror = () => this.options.onError?.(new Error("WebSocket transport error."));
    socket.onclose = () => {
      this.connected = false;
    };
  }

  disconnect() {
    if (!this.socket || this.socket.readyState === WebSocket.CLOSED) {
      return;
    }
    if (this.connected) {
      this.sendFrame("DISCONNECT", {});
    }
    this.socket.close();
  }

  subscribeToRoom(roomId: string) {
    return this.subscribe(`/topic/rooms/${roomId}`);
  }

  subscribeToUserQueue() {
    return this.subscribe("/user/queue/events");
  }

  private subscribe(destination: string) {
    const id = `sub-${this.nextSubscriptionId}`;
    this.nextSubscriptionId += 1;
    this.sendFrame("SUBSCRIBE", {
      id,
      destination,
    });
    return id;
  }

  private handleMessage(raw: string) {
    for (const frame of raw.split("\0").filter(Boolean)) {
      const { command, headers, body } = parseFrame(frame);
      if (command === "CONNECTED") {
        this.connected = true;
        return;
      }
      if (command === "MESSAGE") {
        const event = JSON.parse(body) as RealtimeEvent;
        if (headers.destination === "/user/queue/events" || headers.destination.startsWith("/queue/")) {
          this.options.onUserEvent?.(event);
        } else {
          this.options.onRoomEvent?.(event);
        }
        return;
      }
      if (command === "ERROR") {
        this.options.onError?.(new Error(body || "WebSocket broker error."));
      }
    }
  }

  private sendFrame(command: string, headers: Record<string, string>, body = "") {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      this.options.onError?.(new Error("WebSocket is not open."));
      return;
    }

    const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${escapeHeader(value)}`);
    this.socket.send([command, ...headerLines, "", body].join("\n") + "\0");
  }

  private wsUrl(path: string) {
    const url = new URL(this.options.baseUrl);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    url.pathname = path;
    url.search = "";
    return url.toString();
  }
}

function parseFrame(frame: string) {
  const [headerBlock, ...bodyParts] = frame.split("\n\n");
  const [command, ...headerLines] = headerBlock.split("\n");
  const headers = Object.fromEntries(
    headerLines
      .filter((line) => line.includes(":"))
      .map((line) => {
        const separator = line.indexOf(":");
        return [line.slice(0, separator), unescapeHeader(line.slice(separator + 1))];
      }),
  );
  return {
    command,
    headers,
    body: bodyParts.join("\n\n"),
  };
}

function escapeHeader(value: string) {
  return value.replace(/\\/g, "\\\\").replace(/\n/g, "\\n").replace(/:/g, "\\c");
}

function unescapeHeader(value: string) {
  return value.replace(/\\c/g, ":").replace(/\\n/g, "\n").replace(/\\\\/g, "\\");
}
