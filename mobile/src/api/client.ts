import type {
  AuthResponse,
  GameResponse,
  RoomPreviewResponse,
  RoomResponse,
  RoomSettingsResponse,
  RoomSummaryResponse,
} from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly body: unknown,
  ) {
    super(message);
  }
}

export interface CreateRoomPayload {
  writingStyle: RoomSettingsResponse["writingStyle"];
  language: string;
  safetyMode: RoomSettingsResponse["safetyMode"];
  maxPlayers: number;
  turnLimit: number;
  turnTimeoutSeconds: number;
  visibility: RoomSettingsResponse["visibility"];
}

export type UpdateRoomSettingsPayload = CreateRoomPayload;

export interface SubmitWordPayload {
  word: string;
}

export class ChainStoriesApiClient {
  constructor(
    private readonly baseUrl: string,
    private readonly accessToken?: string,
  ) {}

  withAccessToken(accessToken: string) {
    return new ChainStoriesApiClient(this.baseUrl, accessToken);
  }

  register(email: string, password: string, displayName: string) {
    return this.request<AuthResponse>("/auth/register", {
      method: "POST",
      body: { email, password, displayName },
      authenticated: false,
    });
  }

  login(email: string, password: string) {
    return this.request<AuthResponse>("/auth/login", {
      method: "POST",
      body: { email, password },
      authenticated: false,
    });
  }

  createRoom(payload: CreateRoomPayload) {
    return this.request<RoomResponse>("/rooms", {
      method: "POST",
      body: payload,
    });
  }

  listRooms() {
    return this.request<RoomSummaryResponse[]>("/rooms");
  }

  previewRoom(roomCode: string) {
    return this.request<RoomPreviewResponse>(`/rooms/code/${encodeURIComponent(roomCode)}/preview`);
  }

  joinRoom(roomCode: string) {
    return this.request<RoomResponse>(`/rooms/${encodeURIComponent(roomCode)}/join`, {
      method: "POST",
    });
  }

  getRoom(roomId: string) {
    return this.request<RoomResponse>(`/rooms/${encodeURIComponent(roomId)}`);
  }

  closeRoom(roomId: string) {
    return this.request<RoomResponse>(`/rooms/${encodeURIComponent(roomId)}/close`, {
      method: "POST",
    });
  }

  updateRoomSettings(roomId: string, payload: UpdateRoomSettingsPayload) {
    return this.request<RoomResponse>(`/rooms/${encodeURIComponent(roomId)}/settings`, {
      method: "PATCH",
      body: payload,
    });
  }

  leaveRoom(roomId: string) {
    return this.request<RoomResponse>(`/rooms/${encodeURIComponent(roomId)}/leave`, {
      method: "POST",
    });
  }

  kickParticipant(roomId: string, userId: string) {
    return this.request<RoomResponse>(
      `/rooms/${encodeURIComponent(roomId)}/participants/${encodeURIComponent(userId)}/kick`,
      {
        method: "POST",
      },
    );
  }

  startGame(roomId: string) {
    return this.request<GameResponse>(`/rooms/${encodeURIComponent(roomId)}/games/start`, {
      method: "POST",
    });
  }

  getGame(gameId: string) {
    return this.request<GameResponse>(`/games/${encodeURIComponent(gameId)}`);
  }

  submitWord(gameId: string, turnId: string, payload: SubmitWordPayload) {
    return this.request<GameResponse>(
      `/games/${encodeURIComponent(gameId)}/turns/${encodeURIComponent(turnId)}/submit-word`,
      {
        method: "POST",
        body: payload,
      },
    );
  }

  private async request<T>(
    path: string,
    options: {
      method?: "GET" | "POST" | "PATCH" | "DELETE";
      body?: unknown;
      authenticated?: boolean;
    } = {},
  ): Promise<T> {
    const authenticated = options.authenticated ?? true;
    const headers: Record<string, string> = {
      Accept: "application/json",
    };
    if (options.body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    if (authenticated) {
      if (!this.accessToken) {
        throw new Error("Access token is required for this request.");
      }
      headers.Authorization = `Bearer ${this.accessToken}`;
    }

    const response = await fetch(`${this.baseUrl}/api/v1${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
    const text = await response.text();
    const body = text.length > 0 ? JSON.parse(text) : null;

    if (!response.ok) {
      throw new ApiError("API request failed.", response.status, body);
    }

    return body as T;
  }
}
