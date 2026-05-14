export type RoomStatus = "LOBBY" | "ACTIVE" | "CLOSED" | "EXPIRED" | "BANNED";
export type RoomParticipantRole = "HOST" | "PLAYER";
export type RoomParticipantStatus = "JOINED" | "LEFT" | "KICKED";
export type WritingStyle =
  | "FUNNY"
  | "HORROR"
  | "BATSHIT_CRAZY"
  | "NOIR_DETECTIVE"
  | "FAIRY_TALE"
  | "MANGA_ACTION"
  | "FAMILY_FRIENDLY"
  | "SWISS_CHAOS";
export type SafetyMode = "FAMILY" | "TEEN";
export type RoomVisibility = "PRIVATE" | "PUBLIC";
export type GameStatus = "ACTIVE" | "VOTING" | "FINISHED";
export type GameTurnStatus = "ACTIVE" | "SUBMITTED" | "SKIPPED";
export type VoteCategory =
  | "FUNNIEST_WORD"
  | "BEST_SABOTAGE"
  | "WEIRDEST_TWIST"
  | "BEST_AI_SENTENCE"
  | "MVP_PLAYER";

export type RealtimeEventType =
  | "PLAYER_JOINED"
  | "PLAYER_LEFT"
  | "PLAYER_KICKED"
  | "ROOM_CLOSED"
  | "GAME_STARTED"
  | "TURN_STARTED"
  | "WORD_SUBMITTED"
  | "AI_GENERATION_STARTED"
  | "STORY_SEGMENT_ADDED"
  | "TURN_SKIPPED"
  | "VOTING_STARTED"
  | "VOTE_RESULTS_UPDATED"
  | "GAME_FINISHED"
  | "ERROR_EVENT";

export interface AuthResponse {
  userId: string;
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
}

export interface MeResponse {
  userId: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  favoriteStyle: string | null;
  status: string;
  role: string;
}

export interface RoomSettingsResponse {
  writingStyle: WritingStyle;
  language: string;
  safetyMode: SafetyMode;
  maxPlayers: number;
  turnLimit: number;
  turnTimeoutSeconds: number;
  visibility: RoomVisibility;
}

export interface RoomParticipantResponse {
  userId: string;
  displayName: string;
  role: RoomParticipantRole;
  status: RoomParticipantStatus;
}

export interface RoomResponse {
  roomId: string;
  roomCode: string;
  status: RoomStatus;
  hostUserId: string;
  settings: RoomSettingsResponse;
  participants: RoomParticipantResponse[];
}

export interface RoomPreviewResponse {
  roomCode: string;
  status: RoomStatus;
  hostDisplayName: string;
  settings: RoomSettingsResponse;
  activePlayers: number;
  alreadyJoined: boolean;
  canJoin: boolean;
}

export interface RoomSummaryResponse {
  roomId: string;
  roomCode: string;
  status: RoomStatus;
  hostUserId: string;
  hostDisplayName: string;
  myRole: RoomParticipantRole;
  settings: RoomSettingsResponse;
  activePlayers: number;
}

export interface GameTurnResponse {
  turnId: string;
  turnNumber: number;
  playerUserId: string;
  status: GameTurnStatus;
  startedAt: string;
  expiresAt: string;
  submittedAt: string | null;
}

export interface StorySegmentResponse {
  segmentId: string;
  sequenceNumber: number;
  turnNumber: number | null;
  authorUserId: string | null;
  content: string;
}

export interface GameResponse {
  gameId: string;
  roomId: string;
  status: GameStatus;
  currentTurnNumber: number;
  turnLimit: number;
  turnTimeoutSeconds: number;
  startedAt: string;
  completedAt: string | null;
  currentTurn: GameTurnResponse;
  turnOrder: string[];
  turns: GameTurnResponse[];
  fullStory: string;
  storySegments: StorySegmentResponse[];
}

export interface RandomWordSuggestionResponse {
  word: string;
  normalizedWord: string;
  safetyLevel: SafetyMode;
  writingStyle: WritingStyle;
  language: string;
}

export interface VoteResponse {
  voteId: string;
  gameId: string;
  voterUserId: string;
  category: VoteCategory;
  targetUserId: string | null;
  targetStorySegmentId: string | null;
  createdAt: string;
}

export interface VoteTargetResultResponse {
  targetUserId: string | null;
  targetStorySegmentId: string | null;
  voteCount: number;
}

export interface VoteCategoryResultResponse {
  category: VoteCategory;
  results: VoteTargetResultResponse[];
}

export interface VoteResultsResponse {
  gameId: string;
  categories: VoteCategoryResultResponse[];
}

export interface RealtimeEvent<TPayload = unknown> {
  type: RealtimeEventType;
  roomId: string | null;
  gameId: string | null;
  payload: TPayload;
  occurredAt: string;
}
