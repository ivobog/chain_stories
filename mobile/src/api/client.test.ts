import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, ChainStoriesApiClient, type CreateRoomPayload } from "./client";

const BASE_URL = "http://localhost:8080";
const ACCESS_TOKEN = "access-token";

describe("ChainStoriesApiClient", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("registers and logs in without requiring an access token", async () => {
    const registerFetch = mockJsonResponse({
      userId: "user-id",
      accessToken: "access-token",
      refreshToken: "refresh-token",
      tokenType: "Bearer",
    });

    await new ChainStoriesApiClient(BASE_URL).register("new@example.com", "secret", "New Player");

    expect(registerFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/auth/register`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email: "new@example.com",
        password: "secret",
        displayName: "New Player",
      }),
    });

    const loginFetch = mockJsonResponse({
      userId: "user-id",
      accessToken: "access-token",
      refreshToken: "refresh-token",
      tokenType: "Bearer",
    });

    await new ChainStoriesApiClient(BASE_URL).login("new@example.com", "secret");

    expect(loginFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/auth/login`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email: "new@example.com",
        password: "secret",
      }),
    });
  });

  it("lists authenticated rooms for the signed-in home screen", async () => {
    const fetchMock = mockJsonResponse([
      {
        roomId: "room-id",
        roomCode: "ROOM42",
        status: "LOBBY",
        hostUserId: "host-id",
        hostDisplayName: "Host",
        myRole: "HOST",
        settings: roomSettings(),
        activePlayers: 1,
      },
    ]);

    const rooms = await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).listRooms();

    expect(rooms).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/api/v1/rooms`, {
      method: "GET",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
      },
      body: undefined,
    });
  });

  it("refreshes sessions without requiring an access token", async () => {
    const fetchMock = mockJsonResponse({
      userId: "user-id",
      accessToken: "new-access-token",
      refreshToken: "new-refresh-token",
      tokenType: "Bearer",
    });

    const auth = await new ChainStoriesApiClient(BASE_URL).refresh("old-refresh-token");

    expect(auth.accessToken).toBe("new-access-token");
    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/api/v1/auth/refresh`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ refreshToken: "old-refresh-token" }),
    });
  });

  it("logs out by revoking refresh tokens without requiring an access token", async () => {
    const fetchMock = mockJsonResponse(null);

    await new ChainStoriesApiClient(BASE_URL).logout("refresh-token");

    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/api/v1/auth/logout`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ refreshToken: "refresh-token" }),
    });
  });

  it("sends authenticated preview room requests with encoded room codes", async () => {
    const fetchMock = mockJsonResponse({
      roomCode: "ABC 123",
      status: "LOBBY",
      hostDisplayName: "Host",
      settings: roomSettings(),
      activePlayers: 1,
      alreadyJoined: false,
      canJoin: true,
    });

    const response = await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).previewRoom("ABC 123");

    expect(response.canJoin).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/api/v1/rooms/code/ABC%20123/preview`, {
      method: "GET",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
      },
      body: undefined,
    });
  });

  it("patches room settings with the expected JSON payload", async () => {
    const payload: CreateRoomPayload = {
      writingStyle: "HORROR",
      language: "en",
      safetyMode: "TEEN",
      maxPlayers: 4,
      turnLimit: 10,
      turnTimeoutSeconds: 60,
      visibility: "PRIVATE",
    };
    const fetchMock = mockJsonResponse({ roomId: "room-id", roomCode: "ROOM42" });

    await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).updateRoomSettings("room id", payload);

    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/api/v1/rooms/room%20id/settings`, {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
  });

  it("creates rooms and joins rooms by code", async () => {
    const payload: CreateRoomPayload = {
      writingStyle: "FUNNY",
      language: "en",
      safetyMode: "FAMILY",
      maxPlayers: 4,
      turnLimit: 8,
      turnTimeoutSeconds: 45,
      visibility: "PRIVATE",
    };
    const createFetch = mockJsonResponse(roomResponse());

    await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).createRoom(payload);

    expect(createFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/rooms`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });

    const joinFetch = mockJsonResponse(roomResponse());

    await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).joinRoom("ROOM 42");

    expect(joinFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/rooms/ROOM%2042/join`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
      },
      body: undefined,
    });
  });

  it("posts lobby lifecycle actions for leave, close, kick, and start", async () => {
    const client = new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN);

    const leaveFetch = mockJsonResponse(roomResponse());
    await client.leaveRoom("room-id");
    expect(leaveFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/rooms/room-id/leave`, postWithoutBodyHeaders());

    const closeFetch = mockJsonResponse(roomResponse());
    await client.closeRoom("room-id");
    expect(closeFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/rooms/room-id/close`, postWithoutBodyHeaders());

    const kickFetch = mockJsonResponse(roomResponse());
    await client.kickParticipant("room-id", "user id");
    expect(kickFetch).toHaveBeenCalledWith(
      `${BASE_URL}/api/v1/rooms/room-id/participants/user%20id/kick`,
      postWithoutBodyHeaders(),
    );

    const startFetch = mockJsonResponse(gameResponse());
    await client.startGame("room-id");
    expect(startFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/rooms/room-id/games/start`, postWithoutBodyHeaders());
  });

  it("loads and updates the signed-in profile", async () => {
    const meFetch = mockJsonResponse({
      userId: "user-id",
      email: "player@example.com",
      displayName: "Player",
      avatarUrl: null,
      favoriteStyle: null,
      status: "ACTIVE",
      role: "USER",
    });

    await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).me();

    expect(meFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/me`, {
      method: "GET",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
      },
      body: undefined,
    });

    const updateFetch = mockJsonResponse({ displayName: "New Name" });

    await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).updateProfile({
      displayName: "New Name",
      avatarUrl: null,
      favoriteStyle: "FUNNY",
    });

    expect(updateFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/me/profile`, {
      method: "PATCH",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        displayName: "New Name",
        avatarUrl: null,
        favoriteStyle: "FUNNY",
      }),
    });
  });

  it("posts player-target votes with the expected body", async () => {
    const fetchMock = mockJsonResponse({
      voteId: "vote-id",
      gameId: "game-id",
      voterUserId: "voter-id",
      category: "MVP_PLAYER",
      targetUserId: "target-user-id",
      targetStorySegmentId: null,
      createdAt: "2026-05-14T12:00:00Z",
    });

    await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).submitVote("game-id", {
      category: "MVP_PLAYER",
      targetUserId: "target-user-id",
    });

    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/api/v1/games/game-id/votes`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        category: "MVP_PLAYER",
        targetUserId: "target-user-id",
      }),
    });
  });

  it("fetches the active game for a room resume flow", async () => {
    const fetchMock = mockJsonResponse(gameResponse());

    const game = await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).getRoomGame("room id");

    expect(game.gameId).toBe("game-id");
    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/api/v1/rooms/room%20id/game`, {
      method: "GET",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
      },
      body: undefined,
    });
  });

  it("submits words to the current turn endpoint", async () => {
    const fetchMock = mockJsonResponse(gameResponse());

    await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).submitWord("game id", "turn id", { word: "banana" });

    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/api/v1/games/game%20id/turns/turn%20id/submit-word`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ word: "banana" }),
    });
  });

  it("requests random words and skips expired turns with POST requests", async () => {
    const randomWordFetch = mockJsonResponse({
      word: "meteor",
      normalizedWord: "meteor",
      safetyLevel: "TEEN",
      writingStyle: "FUNNY",
      language: "en",
    });

    await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).randomWord("game-id");

    expect(randomWordFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/games/game-id/random-word`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
      },
      body: undefined,
    });

    const skipFetch = mockJsonResponse(gameResponse());

    await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).skipExpiredTurn("game-id", "turn-id");

    expect(skipFetch).toHaveBeenCalledWith(`${BASE_URL}/api/v1/games/game-id/turns/turn-id/skip-expired`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
      },
      body: undefined,
    });
  });

  it("fetches vote results for voting and finished games", async () => {
    const fetchMock = mockJsonResponse({
      gameId: "game-id",
      categories: [
        {
          category: "MVP_PLAYER",
          results: [{ targetUserId: "user-id", targetStorySegmentId: null, voteCount: 2 }],
        },
      ],
    });

    const results = await new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).voteResults("game-id");

    expect(results.categories).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/api/v1/games/game-id/votes/results`, {
      method: "GET",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${ACCESS_TOKEN}`,
      },
      body: undefined,
    });
  });

  it("throws ApiError with parsed response bodies", async () => {
    mockJsonResponse(
      {
        errorCode: "ROOM_CLOSED",
        message: "Room is no longer joinable.",
      },
      409,
    );

    await expect(new ChainStoriesApiClient(BASE_URL, ACCESS_TOKEN).joinRoom("ROOM42")).rejects.toMatchObject({
      status: 409,
      body: {
        errorCode: "ROOM_CLOSED",
        message: "Room is no longer joinable.",
      },
    } satisfies Partial<ApiError>);
  });
});

function roomSettings() {
  return {
    writingStyle: "FUNNY",
    language: "en",
    safetyMode: "TEEN",
    maxPlayers: 4,
    turnLimit: 8,
    turnTimeoutSeconds: 45,
    visibility: "PRIVATE",
  };
}

function roomResponse() {
  return {
    roomId: "room-id",
    roomCode: "ROOM42",
    status: "LOBBY",
    hostUserId: "host-id",
    settings: roomSettings(),
    participants: [],
  };
}

function gameResponse() {
  return {
    gameId: "game-id",
    roomId: "room-id",
    status: "ACTIVE",
    currentTurnNumber: 1,
    turnLimit: 8,
    turnTimeoutSeconds: 45,
    startedAt: "2026-05-14T12:00:00Z",
    completedAt: null,
    currentTurn: {
      turnId: "turn-id",
      turnNumber: 1,
      playerUserId: "user-id",
      status: "ACTIVE",
      startedAt: "2026-05-14T12:00:00Z",
      expiresAt: "2026-05-14T12:00:45Z",
      submittedAt: null,
    },
    turnOrder: ["user-id"],
    turns: [],
    fullStory: "Once upon a time.",
    storySegments: [],
  };
}

function postWithoutBodyHeaders() {
  return {
    method: "POST",
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${ACCESS_TOKEN}`,
    },
    body: undefined,
  };
}

function mockJsonResponse(body: unknown, status = 200) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    text: vi.fn().mockResolvedValue(JSON.stringify(body)),
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}
