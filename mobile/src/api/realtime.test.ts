import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RealtimeConnection } from "./realtime";

const BASE_URL = "http://localhost:8080";
const ACCESS_TOKEN = "access-token";

describe("RealtimeConnection", () => {
  let sockets: MockWebSocket[];

  beforeEach(() => {
    sockets = [];
    vi.stubGlobal(
      "WebSocket",
      class extends MockWebSocket {
        constructor(url: string) {
          super(url, sockets);
        }
      },
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("connects with auth and flushes queued subscriptions after CONNECTED", () => {
    const connection = new RealtimeConnection({
      baseUrl: BASE_URL,
      accessToken: ACCESS_TOKEN,
    });

    connection.connect();
    connection.subscribeToRoom("room-id");
    connection.subscribeToUserQueue();

    const socket = sockets[0];
    expect(socket.url).toBe("ws://localhost:8080/ws/game");
    expect(socket.sentFrames).toHaveLength(0);

    socket.emitOpen();
    expect(socket.sentFrames[0]).toContain("CONNECT\n");
    expect(socket.sentFrames[0]).toContain(`Authorization:Bearer ${ACCESS_TOKEN}`);

    socket.emitMessage("CONNECTED\nversion:1.2\n\n\0");

    expect(socket.sentFrames).toContain(
      frame("SUBSCRIBE", {
        id: "sub-1",
        destination: "/topic/rooms/room-id",
      }),
    );
    expect(socket.sentFrames).toContain(
      frame("SUBSCRIBE", {
        id: "sub-2",
        destination: "/user/queue/events",
      }),
    );
  });

  it("routes room and user queue messages to the right handlers", () => {
    const onRoomEvent = vi.fn();
    const onUserEvent = vi.fn();
    const connection = new RealtimeConnection({
      baseUrl: BASE_URL,
      accessToken: ACCESS_TOKEN,
      onRoomEvent,
      onUserEvent,
    });

    connection.connect();
    const socket = sockets[0];
    socket.emitMessage(
      frame(
        "MESSAGE",
        { destination: "/topic/rooms/room-id" },
        JSON.stringify({ type: "PLAYER_JOINED", roomId: "room-id", gameId: null, payload: {}, occurredAt: "now" }),
      ),
    );
    socket.emitMessage(
      frame(
        "MESSAGE",
        { destination: "/user/queue/events" },
        JSON.stringify({ type: "PLAYER_KICKED", roomId: "room-id", gameId: null, payload: {}, occurredAt: "now" }),
      ),
    );

    expect(onRoomEvent).toHaveBeenCalledWith(expect.objectContaining({ type: "PLAYER_JOINED" }));
    expect(onUserEvent).toHaveBeenCalledWith(expect.objectContaining({ type: "PLAYER_KICKED" }));
  });
});

function frame(command: string, headers: Record<string, string>, body = "") {
  const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
  return [command, ...headerLines, "", body].join("\n") + "\0";
}

class MockWebSocket {
  static readonly OPEN = 1;
  static readonly CLOSED = 3;

  readonly sentFrames: string[] = [];
  readyState = MockWebSocket.OPEN;
  onopen?: () => void;
  onmessage?: (message: { data: string }) => void;
  onerror?: () => void;
  onclose?: () => void;

  constructor(
    readonly url: string,
    sockets: MockWebSocket[],
  ) {
    sockets.push(this);
  }

  send(frameText: string) {
    this.sentFrames.push(frameText);
  }

  close() {
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.();
  }

  emitOpen() {
    this.onopen?.();
  }

  emitMessage(data: string) {
    this.onmessage?.({ data });
  }
}
