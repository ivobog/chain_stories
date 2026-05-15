import * as SecureStore from "expo-secure-store";
import { StatusBar } from "expo-status-bar";
import { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  AppState,
  Pressable,
  SafeAreaView,
  ScrollView,
  Share,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";

import {
  ApiError,
  ChainStoriesApiClient,
  RealtimeConnection,
  type AuthResponse,
  type CreateRoomPayload,
  type GameResponse,
  type MeResponse,
  type PlayWithBotPayload,
  type RandomWordSuggestionResponse,
  type RoomPreviewResponse,
  type RealtimeEvent,
  type RoomVisibility,
  type RoomResponse,
  type RoomSummaryResponse,
  type SafetyMode,
  type UpdateRoomSettingsPayload,
  type VoteCategory,
  type VoteResultsResponse,
  type WritingStyle,
} from "./src/api";
import { renderableStoryText } from "./src/story-text";
import { WRITING_STYLE_GROUPS, styleLabel } from "./src/writing-styles";

const DEFAULT_API_BASE_URL = "http://localhost:8080";
const SESSION_KEYS = {
  accessToken: "chainStories.accessToken",
  refreshToken: "chainStories.refreshToken",
  userId: "chainStories.userId",
  apiBaseUrl: "chainStories.apiBaseUrl",
};

type AuthMode = "login" | "register";

const VOTE_CATEGORIES: VoteCategory[] = [
  "FUNNIEST_WORD",
  "BEST_SABOTAGE",
  "WEIRDEST_TWIST",
  "BEST_AI_SENTENCE",
  "MVP_PLAYER",
];

const SAFETY_MODES: SafetyMode[] = ["TEEN", "FAMILY"];
const VISIBILITIES: RoomVisibility[] = ["PRIVATE", "PUBLIC"];

interface Session {
  userId: string;
  accessToken: string;
  refreshToken: string;
}

const defaultRoomPayload: CreateRoomPayload = {
  writingStyle: "FUNNY",
  language: "en",
  safetyMode: "TEEN",
  maxPlayers: 4,
  turnLimit: 8,
  turnTimeoutSeconds: 45,
  visibility: "PRIVATE",
};

const defaultPlayWithBotPayload: PlayWithBotPayload = {
  writingStyle: "FUNNY",
  language: "en",
  safetyMode: "TEEN",
  turnLimit: 10,
  turnTimeoutSeconds: 60,
};

export default function App() {
  const [booting, setBooting] = useState(true);
  const [apiBaseUrl, setApiBaseUrl] = useState(DEFAULT_API_BASE_URL);
  const [session, setSession] = useState<Session | null>(null);
  const [rooms, setRooms] = useState<RoomSummaryResponse[]>([]);
  const [activeRoom, setActiveRoom] = useState<RoomResponse | null>(null);
  const [activeGame, setActiveGame] = useState<GameResponse | null>(null);
  const [profile, setProfile] = useState<MeResponse | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const [showCreateRoom, setShowCreateRoom] = useState(false);
  const [showJoinRoom, setShowJoinRoom] = useState(false);
  const [roomPreview, setRoomPreview] = useState<RoomPreviewResponse | null>(null);
  const [suggestion, setSuggestion] = useState<RandomWordSuggestionResponse | null>(null);
  const [voteResults, setVoteResults] = useState<VoteResultsResponse | null>(null);
  const [votedCategories, setVotedCategories] = useState<VoteCategory[]>([]);
  const [aiGeneratingGameId, setAiGeneratingGameId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [busyLabel, setBusyLabel] = useState("");

  const api = useMemo(
    () => new ChainStoriesApiClient(apiBaseUrl, session?.accessToken),
    [apiBaseUrl, session?.accessToken],
  );

  useEffect(() => {
    restoreSession();
  }, []);

  useEffect(() => {
    if (!session) {
      return;
    }

    let cancelled = false;
    setBusyLabel("Loading rooms");
    setError("");
    api
      .listRooms()
      .then((nextRooms) => {
        if (!cancelled) {
          setRooms(nextRooms);
        }
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(readableError(caught));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setBusyLabel((current) => (current === "Loading rooms" ? "" : current));
        }
      });

    return () => {
      cancelled = true;
    };
  }, [api, session?.userId]);

  useEffect(() => {
    if (!session || !activeRoom) {
      return;
    }

    const realtime = new RealtimeConnection({
      baseUrl: apiBaseUrl,
      accessToken: session.accessToken,
      onRoomEvent: handleRoomEvent,
      onUserEvent: handleUserEvent,
      onError: (realtimeError) => setError(realtimeError.message),
    });
    realtime.connect();
    realtime.subscribeToRoom(activeRoom.roomId);
    realtime.subscribeToUserQueue();

    return () => realtime.disconnect();
  }, [apiBaseUrl, session?.accessToken, activeRoom?.roomId]);

  useEffect(() => {
    if (!session) {
      return;
    }

    const subscription = AppState.addEventListener("change", (nextState) => {
      if (nextState === "active") {
        void refreshVisibleState();
      }
    });

    return () => subscription.remove();
  }, [api, session?.userId, activeRoom?.roomId, activeGame?.gameId]);

  async function restoreSession() {
    try {
      const [storedAccessToken, storedRefreshToken, storedUserId, storedApiBaseUrl] = await Promise.all([
        SecureStore.getItemAsync(SESSION_KEYS.accessToken),
        SecureStore.getItemAsync(SESSION_KEYS.refreshToken),
        SecureStore.getItemAsync(SESSION_KEYS.userId),
        SecureStore.getItemAsync(SESSION_KEYS.apiBaseUrl),
      ]);
      if (storedApiBaseUrl) {
        setApiBaseUrl(storedApiBaseUrl);
      }
      if (storedAccessToken && storedRefreshToken && storedUserId) {
        const restoredBaseUrl = storedApiBaseUrl || DEFAULT_API_BASE_URL;
        try {
          const refreshedAuth = await new ChainStoriesApiClient(restoredBaseUrl).refresh(storedRefreshToken);
          await persistSession(refreshedAuth, restoredBaseUrl);
        } catch (caught) {
          if (caught instanceof ApiError) {
            await clearPersistedSession();
            setError("Session expired. Sign in again.");
          } else {
            setSession({
              accessToken: storedAccessToken,
              refreshToken: storedRefreshToken,
              userId: storedUserId,
            });
            setError("Could not refresh session. Using the stored session for now.");
          }
        }
      }
    } finally {
      setBooting(false);
    }
  }

  async function persistSession(auth: AuthResponse, baseUrl: string) {
    await Promise.all([
      SecureStore.setItemAsync(SESSION_KEYS.accessToken, auth.accessToken),
      SecureStore.setItemAsync(SESSION_KEYS.refreshToken, auth.refreshToken),
      SecureStore.setItemAsync(SESSION_KEYS.userId, auth.userId),
      SecureStore.setItemAsync(SESSION_KEYS.apiBaseUrl, baseUrl),
    ]);
    setSession(auth);
  }

  async function clearPersistedSession() {
    await Promise.all(Object.values(SESSION_KEYS).map((key) => SecureStore.deleteItemAsync(key)));
  }

  async function clearSession() {
    setBusyLabel("Signing out");
    try {
      if (session?.refreshToken) {
        await new ChainStoriesApiClient(apiBaseUrl).logout(session.refreshToken).catch(() => undefined);
      }
      await clearPersistedSession();
      setSession(null);
      setRooms([]);
      setActiveRoom(null);
      setActiveGame(null);
      setProfile(null);
      setShowSettings(false);
      setShowCreateRoom(false);
      setShowJoinRoom(false);
      setRoomPreview(null);
      setSuggestion(null);
      setVoteResults(null);
      setVotedCategories([]);
      setAiGeneratingGameId(null);
      setError("");
    } finally {
      setBusyLabel("");
    }
  }

  async function runAction(label: string, action: () => Promise<void>) {
    setBusyLabel(label);
    setError("");
    try {
      await action();
    } catch (caught) {
      setError(readableError(caught));
    } finally {
      setBusyLabel("");
    }
  }

  async function runApiAction(label: string, action: (client: ChainStoriesApiClient) => Promise<void>) {
    await runAction(label, async () => {
      await withAuthorizedApi(action);
    });
  }

  async function withAuthorizedApi<T>(action: (client: ChainStoriesApiClient) => Promise<T>): Promise<T> {
    try {
      return await action(api);
    } catch (caught) {
      if (!isAuthExpired(caught) || !session?.refreshToken) {
        throw caught;
      }

      try {
        const refreshedAuth = await new ChainStoriesApiClient(apiBaseUrl).refresh(session.refreshToken);
        await persistSession(refreshedAuth, apiBaseUrl);
        return await action(new ChainStoriesApiClient(apiBaseUrl, refreshedAuth.accessToken));
      } catch (refreshError) {
        if (refreshError instanceof ApiError) {
          await clearPersistedSession();
          setSession(null);
        }
        throw refreshError;
      }
    }
  }

  async function refreshVisibleState() {
    if (!session) {
      return;
    }

    try {
      if (activeGame) {
        const [nextGame, nextRoom] = await Promise.all([
          api.getGame(activeGame.gameId),
          activeRoom ? api.getRoom(activeRoom.roomId) : Promise.resolve(null),
        ]);
        setActiveGame(nextGame);
        setAiGeneratingGameId(null);
        if (nextRoom) {
          setActiveRoom(nextRoom);
        }
        if (nextGame.status === "VOTING" || nextGame.status === "FINISHED") {
          setVoteResults(await api.voteResults(nextGame.gameId));
        } else {
          setVoteResults(null);
        }
        return;
      }

      if (activeRoom) {
        const nextRoom = await api.getRoom(activeRoom.roomId);
        setActiveRoom(nextRoom);
        if (nextRoom.status === "ACTIVE") {
          const nextGame = await api.getRoomGame(nextRoom.roomId);
          setActiveGame(nextGame);
          setAiGeneratingGameId(null);
          if (nextGame.status === "VOTING" || nextGame.status === "FINISHED") {
            setVoteResults(await api.voteResults(nextGame.gameId));
          }
        }
        return;
      }

      setRooms(await api.listRooms());
    } catch (caught) {
      setError(readableError(caught));
    }
  }

  function clearActiveRoomState(message: string) {
    setActiveRoom(null);
    setActiveGame(null);
    setSuggestion(null);
    setVoteResults(null);
    setVotedCategories([]);
    setAiGeneratingGameId(null);
    setError(message);
    void api.listRooms().then(setRooms).catch(() => undefined);
  }

  function handleRoomEvent(event: RealtimeEvent) {
    if (event.type === "ROOM_CLOSED") {
      clearActiveRoomState("The room was closed.");
      return;
    }

    if (isRoomResponse(event.payload)) {
      setActiveRoom(event.payload);
    }

    if (isGameResponse(event.payload)) {
      const payload = event.payload;
      if (event.type === "GAME_STARTED") {
        setActiveRoom((current) =>
          current && current.roomId === payload.roomId ? { ...current, status: "ACTIVE" } : current,
        );
      }
      if (event.type === "AI_GENERATION_STARTED") {
        setAiGeneratingGameId(payload.gameId);
        setBusyLabel("AI is writing");
      } else {
        setAiGeneratingGameId((current) => (current === payload.gameId ? null : current));
        setBusyLabel("");
      }
      setActiveGame((current) => {
        if (!current || current.gameId === payload.gameId || event.type === "GAME_STARTED") {
          return payload;
        }
        return current;
      });
      if (payload.status === "VOTING" || payload.status === "FINISHED") {
        void api.voteResults(payload.gameId).then(setVoteResults).catch(() => undefined);
      } else {
        setVoteResults(null);
      }
      return;
    }

    if ((event.type === "VOTE_RESULTS_UPDATED" || event.type === "GAME_FINISHED") && isVoteResultsResponse(event.payload)) {
      const payload = event.payload;
      setVoteResults(payload);
      if (event.type === "GAME_FINISHED") {
        setAiGeneratingGameId((current) => (current === payload.gameId ? null : current));
        setActiveGame((current) =>
          current && current.gameId === payload.gameId ? { ...current, status: "FINISHED" } : current,
        );
      }
    }
  }

  function handleUserEvent(event: RealtimeEvent) {
    if (event.type === "PLAYER_KICKED") {
      clearActiveRoomState("You were removed from the room.");
    }
  }

  async function refreshRooms() {
    await runApiAction("Refreshing rooms", async (client) => {
      setRooms(await client.listRooms());
    });
  }

  async function openSettings() {
    await runApiAction("Loading settings", async (client) => {
      setProfile(await client.me());
      setShowSettings(true);
    });
  }

  async function saveProfile(payload: { displayName: string; avatarUrl?: string | null; favoriteStyle?: string | null }) {
    await runApiAction("Saving profile", async (client) => {
      setProfile(await client.updateProfile(payload));
    });
  }

  async function createRoom(payload: CreateRoomPayload) {
    await runApiAction("Creating room", async (client) => {
      const room = await client.createRoom(payload);
      setActiveRoom(room);
      setShowCreateRoom(false);
      setRooms(await client.listRooms());
    });
  }

  async function joinRoom(roomCode: string) {
    await runApiAction("Joining room", async (client) => {
      const room = await client.joinRoom(roomCode.trim().toUpperCase());
      setActiveRoom(room);
      setShowJoinRoom(false);
      setRoomPreview(null);
      setRooms(await client.listRooms());
    });
  }

  async function playWithBot() {
    await runApiAction("Creating bot game", async (client) => {
      const response = await client.playWithBot(defaultPlayWithBotPayload);
      setActiveRoom(response.room);
      setActiveGame(response.game);
      setSuggestion(null);
      setVoteResults(null);
      setVotedCategories([]);
      setAiGeneratingGameId(null);
      setRooms(await client.listRooms());
    });
  }

  async function previewRoom(roomCode: string) {
    await runApiAction("Previewing room", async (client) => {
      setRoomPreview(await client.previewRoom(roomCode.trim().toUpperCase()));
    });
  }

  async function shareRoomCode(roomCode: string) {
    await runAction("Opening share sheet", async () => {
      await Share.share({
        message: `Join my Chain Stories room with code ${roomCode}.`,
      });
    });
  }

  async function shareFinalStory(game: GameResponse) {
    await runAction("Opening share sheet", async () => {
      const story = game.fullStory || game.storySegments.map((segment) => segment.content).join(" ");
      await Share.share({
        message: `Chain Stories final story:\n\n${story}`,
      });
    });
  }

  async function openRoom(roomId: string) {
    await runApiAction("Opening room", async (client) => {
      setActiveRoom(await client.getRoom(roomId));
    });
  }

  async function leaveRoom(roomId: string) {
    await runApiAction("Leaving room", async (client) => {
      await client.leaveRoom(roomId);
      setActiveRoom(null);
      setActiveGame(null);
      setVoteResults(null);
      setVotedCategories([]);
      setAiGeneratingGameId(null);
      setRooms(await client.listRooms());
    });
  }

  async function closeRoom(roomId: string) {
    await runApiAction("Closing room", async (client) => {
      await client.closeRoom(roomId);
      setActiveRoom(null);
      setActiveGame(null);
      setVoteResults(null);
      setVotedCategories([]);
      setAiGeneratingGameId(null);
      setRooms(await client.listRooms());
    });
  }

  async function kickParticipant(roomId: string, userId: string) {
    await runApiAction("Removing player", async (client) => {
      setActiveRoom(await client.kickParticipant(roomId, userId));
      setRooms(await client.listRooms());
    });
  }

  async function updateRoomSettings(roomId: string, payload: UpdateRoomSettingsPayload) {
    await runApiAction("Saving room settings", async (client) => {
      setActiveRoom(await client.updateRoomSettings(roomId, payload));
      setRooms(await client.listRooms());
    });
  }

  async function startGame(roomId: string) {
    await runApiAction("Starting game", async (client) => {
      const game = await client.startGame(roomId);
      setActiveGame(game);
      setVoteResults(null);
      setVotedCategories([]);
      setAiGeneratingGameId(null);
      setActiveRoom(await client.getRoom(roomId));
    });
  }

  async function openRoomGame(roomId: string) {
    await runApiAction("Opening game", async (client) => {
      const [room, game] = await Promise.all([client.getRoom(roomId), client.getRoomGame(roomId)]);
      setActiveRoom(room);
      setActiveGame(game);
      if (game.status === "VOTING" || game.status === "FINISHED") {
        setVoteResults(await client.voteResults(game.gameId));
      } else {
        setVoteResults(null);
      }
      setAiGeneratingGameId(null);
    });
  }

  async function refreshGame(gameId: string) {
    await runApiAction("Refreshing game", async (client) => {
      const game = await client.getGame(gameId);
      setActiveGame(game);
      setAiGeneratingGameId(null);
      if (game.status === "VOTING" || game.status === "FINISHED") {
        setVoteResults(await client.voteResults(gameId));
      } else {
        setVoteResults(null);
      }
    });
  }

  async function submitWord(game: GameResponse, word: string) {
    await runApiAction("Submitting word", async (client) => {
      setAiGeneratingGameId(game.gameId);
      const updatedGame = await client.submitWord(game.gameId, game.currentTurn.turnId, { word });
      setSuggestion(null);
      setActiveGame(updatedGame);
      setAiGeneratingGameId(null);
      if (updatedGame.status === "VOTING" || updatedGame.status === "FINISHED") {
        setVoteResults(await client.voteResults(updatedGame.gameId));
      }
      if (activeRoom) {
        setActiveRoom(await client.getRoom(activeRoom.roomId));
      }
    });
  }

  async function requestRandomWord(game: GameResponse) {
    await runApiAction("Finding a word", async (client) => {
      setSuggestion(await client.randomWord(game.gameId));
    });
  }

  async function skipExpiredTurn(game: GameResponse) {
    await runApiAction("Skipping expired turn", async (client) => {
      setSuggestion(null);
      const updatedGame = await client.skipExpiredTurn(game.gameId, game.currentTurn.turnId);
      setActiveGame(updatedGame);
      setAiGeneratingGameId(null);
      if (updatedGame.status === "VOTING" || updatedGame.status === "FINISHED") {
        setVoteResults(await client.voteResults(updatedGame.gameId));
      }
    });
  }

  async function refreshVoteResults(gameId: string) {
    await runApiAction("Refreshing results", async (client) => {
      setVoteResults(await client.voteResults(gameId));
    });
  }

  async function submitVote(game: GameResponse, category: VoteCategory, targetId: string) {
    await runApiAction("Submitting vote", async (client) => {
      await client.submitVote(game.gameId, {
        category,
        ...(categoryTargetsPlayer(category) ? { targetUserId: targetId } : { targetStorySegmentId: targetId }),
      });
      setVotedCategories((current) => (current.includes(category) ? current : [...current, category]));
      const [updatedGame, results] = await Promise.all([client.getGame(game.gameId), client.voteResults(game.gameId)]);
      setActiveGame(updatedGame);
      setAiGeneratingGameId(null);
      setVoteResults(results);
    });
  }

  if (booting) {
    return (
      <SafeAreaView style={styles.screen}>
        <StatusBar style="dark" />
        <ActivityIndicator color="#0f766e" />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar style="dark" />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.header}>
          <Text style={styles.kicker}>Chain Stories</Text>
          <Text style={styles.title}>{session ? "Play" : "Sign in"}</Text>
        </View>

        {error ? <Banner tone="error" message={error} /> : null}
        {busyLabel ? <Banner tone="neutral" message={busyLabel} /> : null}

        {session ? (
          showCreateRoom ? (
            <CreateRoomScreen
              onBack={() => setShowCreateRoom(false)}
              onCreate={createRoom}
            />
          ) : showJoinRoom ? (
            <JoinRoomScreen
              preview={roomPreview}
              onBack={() => {
                setShowJoinRoom(false);
                setRoomPreview(null);
              }}
              onPreview={previewRoom}
              onJoin={joinRoom}
            />
          ) : showSettings ? (
            <SettingsScreen
              profile={profile}
              apiBaseUrl={apiBaseUrl}
              onBack={() => setShowSettings(false)}
              onRefresh={openSettings}
              onSave={saveProfile}
              onSignOut={clearSession}
            />
          ) : activeGame ? (
            <GameScreen
              game={activeGame}
              room={activeRoom}
              currentUserId={session.userId}
              suggestion={suggestion}
              voteResults={voteResults}
              votedCategories={votedCategories}
              isGenerating={aiGeneratingGameId === activeGame.gameId}
              onBack={() => setActiveGame(null)}
              onRefresh={() => refreshGame(activeGame.gameId)}
              onSubmitWord={(word) => submitWord(activeGame, word)}
              onRandomWord={() => requestRandomWord(activeGame)}
              onUseSuggestion={() => suggestion?.word ?? ""}
              onSkipExpired={() => skipExpiredTurn(activeGame)}
              onRefreshResults={() => refreshVoteResults(activeGame.gameId)}
              onSubmitVote={(category, targetId) => submitVote(activeGame, category, targetId)}
              onShareStory={() => shareFinalStory(activeGame)}
            />
          ) : activeRoom ? (
            <LobbyScreen
              room={activeRoom}
              currentUserId={session.userId}
              onBack={() => setActiveRoom(null)}
              onRefresh={() => openRoom(activeRoom.roomId)}
              onStart={() => startGame(activeRoom.roomId)}
              onOpenGame={() => openRoomGame(activeRoom.roomId)}
              onShareCode={() => shareRoomCode(activeRoom.roomCode)}
              onLeave={() => leaveRoom(activeRoom.roomId)}
              onClose={() => closeRoom(activeRoom.roomId)}
              onKick={(userId) => kickParticipant(activeRoom.roomId, userId)}
              onUpdateSettings={(payload) => updateRoomSettings(activeRoom.roomId, payload)}
              onSignOut={clearSession}
            />
          ) : (
            <HomeScreen
              userId={session.userId}
              apiBaseUrl={apiBaseUrl}
              rooms={rooms}
              onRefreshRooms={refreshRooms}
              onCreateRoom={() => setShowCreateRoom(true)}
              onPlayWithBot={playWithBot}
              onOpenJoinRoom={() => setShowJoinRoom(true)}
              onOpenRoom={openRoom}
              onOpenSettings={openSettings}
              onSignOut={clearSession}
            />
          )
        ) : (
          <AuthScreen
            apiBaseUrl={apiBaseUrl}
            onApiBaseUrlChange={setApiBaseUrl}
            onSubmit={(mode, email, password, displayName) =>
              runAction(mode === "login" ? "Signing in" : "Creating account", async () => {
                const auth =
                  mode === "login"
                    ? await new ChainStoriesApiClient(apiBaseUrl).login(email, password)
                    : await new ChainStoriesApiClient(apiBaseUrl).register(email, password, displayName);
                await persistSession(auth, apiBaseUrl);
              })
            }
          />
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function AuthScreen({
  apiBaseUrl,
  onApiBaseUrlChange,
  onSubmit,
}: {
  apiBaseUrl: string;
  onApiBaseUrlChange: (value: string) => void;
  onSubmit: (mode: AuthMode, email: string, password: string, displayName: string) => void;
}) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");

  return (
    <View style={styles.panel}>
      <View style={styles.segmented}>
        <SegmentButton active={mode === "login"} label="Login" onPress={() => setMode("login")} />
        <SegmentButton active={mode === "register"} label="Register" onPress={() => setMode("register")} />
      </View>
      <Field label="Backend URL" value={apiBaseUrl} onChangeText={onApiBaseUrlChange} autoCapitalize="none" />
      <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" />
      {mode === "register" ? (
        <Field label="Display name" value={displayName} onChangeText={setDisplayName} />
      ) : null}
      <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry />
      <PrimaryButton
        label={mode === "login" ? "Sign in" : "Create account"}
        onPress={() => onSubmit(mode, email.trim(), password, displayName.trim())}
      />
    </View>
  );
}

function HomeScreen({
  userId,
  apiBaseUrl,
  rooms,
  onRefreshRooms,
  onCreateRoom,
  onPlayWithBot,
  onOpenJoinRoom,
  onOpenRoom,
  onOpenSettings,
  onSignOut,
}: {
  userId: string;
  apiBaseUrl: string;
  rooms: RoomSummaryResponse[];
  onRefreshRooms: () => void;
  onCreateRoom: () => void;
  onPlayWithBot: () => void;
  onOpenJoinRoom: () => void;
  onOpenRoom: (roomId: string) => void;
  onOpenSettings: () => void;
  onSignOut: () => void;
}) {
  return (
    <View style={styles.stack}>
      <View style={styles.panel}>
        <Text style={styles.sectionTitle}>Session</Text>
        <Text style={styles.muted}>User {shortId(userId)}</Text>
        <Text style={styles.muted}>{apiBaseUrl}</Text>
        <View style={styles.buttonRow}>
          <SecondaryButton label="Settings" onPress={onOpenSettings} />
          <SecondaryButton label="Sign out" onPress={onSignOut} />
          <SecondaryButton label="Refresh" onPress={onRefreshRooms} />
        </View>
      </View>

      <View style={styles.panel}>
        <Text style={styles.sectionTitle}>Rooms</Text>
        <View style={styles.buttonRow}>
          <PrimaryButton label="Create room" onPress={onCreateRoom} />
          <PrimaryButton label="Play with Bot" onPress={onPlayWithBot} />
          <SecondaryButton label="Join by code" onPress={onOpenJoinRoom} />
        </View>
      </View>

      {rooms.map((room) => (
        <Pressable key={room.roomId} style={styles.listItem} onPress={() => onOpenRoom(room.roomId)}>
          <View>
            <Text style={styles.listTitle}>{room.roomCode}</Text>
            <Text style={styles.muted}>
              {room.status} / {room.activePlayers}/{room.settings.maxPlayers}
            </Text>
          </View>
          <Text style={styles.badge}>{room.myRole}</Text>
        </Pressable>
      ))}

      {rooms.length === 0 ? (
        <View style={styles.emptyState}>
          <Text style={styles.emptyTitle}>No rooms yet</Text>
          <Text style={styles.muted}>Create a room or join one by code to start a story.</Text>
        </View>
      ) : null}
    </View>
  );
}

function JoinRoomScreen({
  preview,
  onBack,
  onPreview,
  onJoin,
}: {
  preview: RoomPreviewResponse | null;
  onBack: () => void;
  onPreview: (roomCode: string) => void;
  onJoin: (roomCode: string) => void;
}) {
  const [roomCode, setRoomCode] = useState(preview?.roomCode ?? "");
  const normalizedRoomCode = roomCode.trim().toUpperCase();
  const previewMatchesInput = preview?.roomCode === normalizedRoomCode;
  const canJoinPreview = Boolean(previewMatchesInput && preview && (preview.canJoin || preview.alreadyJoined));

  return (
    <View style={styles.stack}>
      <View style={styles.panel}>
        <Text style={styles.sectionTitle}>Join Room</Text>
        <Text style={styles.muted}>Preview the room before joining.</Text>
        <View style={styles.buttonRow}>
          <SecondaryButton label="Back" onPress={onBack} />
        </View>
      </View>

      <View style={styles.panel}>
        <Field
          label="Room code"
          value={roomCode}
          onChangeText={(value) => setRoomCode(value.toUpperCase())}
          autoCapitalize="characters"
        />
        <View style={styles.buttonRow}>
          <SecondaryButton label="Preview" onPress={() => onPreview(normalizedRoomCode)} disabled={!normalizedRoomCode} />
          <PrimaryButton
            label={preview?.alreadyJoined ? "Open room" : "Join room"}
            onPress={() => onJoin(preview?.roomCode ?? normalizedRoomCode)}
            disabled={!canJoinPreview}
          />
        </View>
      </View>

      {previewMatchesInput && preview ? (
        <View style={styles.panel}>
          <View style={styles.rowBetween}>
            <View>
              <Text style={styles.sectionTitle}>{preview.roomCode}</Text>
              <Text style={styles.muted}>Hosted by {preview.hostDisplayName}</Text>
            </View>
            <Text style={styles.badge}>{preview.status}</Text>
          </View>
          <View style={styles.detailGrid}>
            <Detail label="Players" value={`${preview.activePlayers}/${preview.settings.maxPlayers}`} />
            <Detail label="Style" value={styleLabel(preview.settings.writingStyle)} />
            <Detail label="Safety" value={preview.settings.safetyMode} />
            <Detail label="Turns" value={String(preview.settings.turnLimit)} />
          </View>
          <Text style={styles.muted}>
            {preview.alreadyJoined
              ? "You are already in this room."
              : preview.canJoin
                ? "This room is ready to join."
                : "This room cannot be joined right now."}
          </Text>
        </View>
      ) : null}
    </View>
  );
}

function CreateRoomScreen({
  onBack,
  onCreate,
}: {
  onBack: () => void;
  onCreate: (payload: CreateRoomPayload) => void;
}) {
  const [writingStyle, setWritingStyle] = useState<WritingStyle>(defaultRoomPayload.writingStyle);
  const [language, setLanguage] = useState(defaultRoomPayload.language);
  const [safetyMode, setSafetyMode] = useState<SafetyMode>(defaultRoomPayload.safetyMode);
  const [visibility, setVisibility] = useState<RoomVisibility>(defaultRoomPayload.visibility);
  const [maxPlayers, setMaxPlayers] = useState(String(defaultRoomPayload.maxPlayers));
  const [turnLimit, setTurnLimit] = useState(String(defaultRoomPayload.turnLimit));
  const [turnTimeoutSeconds, setTurnTimeoutSeconds] = useState(String(defaultRoomPayload.turnTimeoutSeconds));

  const parsedMaxPlayers = boundedNumber(maxPlayers, 2, 8, defaultRoomPayload.maxPlayers);
  const parsedTurnLimit = boundedNumber(turnLimit, 1, 30, defaultRoomPayload.turnLimit);
  const parsedTimeout = boundedNumber(turnTimeoutSeconds, 10, 300, defaultRoomPayload.turnTimeoutSeconds);

  return (
    <View style={styles.stack}>
      <View style={styles.panel}>
        <Text style={styles.sectionTitle}>Create Room</Text>
        <Text style={styles.muted}>Choose settings before inviting players.</Text>
        <View style={styles.buttonRow}>
          <SecondaryButton label="Back" onPress={onBack} />
        </View>
      </View>

      <View style={styles.panel}>
        <Text style={styles.sectionTitle}>Story Setup</Text>
        <GroupedOptionRow
          label="Writing style"
          groups={WRITING_STYLE_GROUPS}
          value={writingStyle}
          onChange={setWritingStyle}
          formatter={styleLabel}
        />
        <OptionRow label="Safety" options={SAFETY_MODES} value={safetyMode} onChange={setSafetyMode} />
        <OptionRow label="Visibility" options={VISIBILITIES} value={visibility} onChange={setVisibility} />
        <Field label="Language" value={language} onChangeText={setLanguage} autoCapitalize="none" />
      </View>

      <View style={styles.panel}>
        <Text style={styles.sectionTitle}>Round Rules</Text>
        <Field label="Max players" value={maxPlayers} onChangeText={setMaxPlayers} keyboardType="number-pad" />
        <Field label="Turn limit" value={turnLimit} onChangeText={setTurnLimit} keyboardType="number-pad" />
        <Field
          label="Turn timeout seconds"
          value={turnTimeoutSeconds}
          onChangeText={setTurnTimeoutSeconds}
          keyboardType="number-pad"
        />
        <Text style={styles.muted}>
          {parsedMaxPlayers} players / {parsedTurnLimit} turns / {parsedTimeout}s timeout
        </Text>
        <PrimaryButton
          label="Create room"
          onPress={() =>
            onCreate({
              writingStyle,
              language: language.trim() || defaultRoomPayload.language,
              safetyMode,
              maxPlayers: parsedMaxPlayers,
              turnLimit: parsedTurnLimit,
              turnTimeoutSeconds: parsedTimeout,
              visibility,
            })
          }
        />
      </View>
    </View>
  );
}

function LobbyScreen({
  room,
  currentUserId,
  onBack,
  onRefresh,
  onStart,
  onOpenGame,
  onShareCode,
  onLeave,
  onClose,
  onKick,
  onUpdateSettings,
  onSignOut,
}: {
  room: RoomResponse;
  currentUserId: string;
  onBack: () => void;
  onRefresh: () => void;
  onStart: () => void;
  onOpenGame: () => void;
  onShareCode: () => void;
  onLeave: () => void;
  onClose: () => void;
  onKick: (userId: string) => void;
  onUpdateSettings: (payload: UpdateRoomSettingsPayload) => void;
  onSignOut: () => void;
}) {
  const [editingSettings, setEditingSettings] = useState(false);
  const isHost = room.hostUserId === currentUserId;
  const canManageLobby = isHost && room.status === "LOBBY";
  const activePlayerCount = room.participants.filter((participant) => participant.status === "JOINED").length;

  return (
    <View style={styles.stack}>
      <View style={styles.panel}>
        <Text style={styles.sectionTitle}>Lobby {room.roomCode}</Text>
        <Text style={styles.muted}>
          {room.status} / {styleLabel(room.settings.writingStyle)} / {room.settings.turnLimit} turns
        </Text>
        <View style={styles.buttonRow}>
          <SecondaryButton label="Back" onPress={onBack} />
          <SecondaryButton label="Refresh" onPress={onRefresh} />
          <SecondaryButton label="Share code" onPress={onShareCode} />
          {canManageLobby ? (
            <SecondaryButton
              label={editingSettings ? "Hide settings" : "Edit settings"}
              onPress={() => setEditingSettings((current) => !current)}
            />
          ) : null}
          {room.status === "LOBBY" ? <SecondaryButton label="Leave room" onPress={onLeave} /> : null}
          <SecondaryButton label="Sign out" onPress={onSignOut} />
        </View>
        {isHost && room.status === "LOBBY" ? <PrimaryButton label="Start game" onPress={onStart} /> : null}
        {room.status === "ACTIVE" ? <PrimaryButton label="Open game" onPress={onOpenGame} /> : null}
        {canManageLobby ? <DangerButton label="Close room" onPress={onClose} /> : null}
      </View>

      {canManageLobby && editingSettings ? (
        <RoomSettingsEditor
          settings={room.settings}
          activePlayerCount={activePlayerCount}
          onSave={(payload) => {
            onUpdateSettings(payload);
            setEditingSettings(false);
          }}
        />
      ) : null}

      {room.participants.map((participant) => (
        <View key={participant.userId} style={styles.listItem}>
          <View>
            <Text style={styles.listTitle}>{participant.displayName}</Text>
            <Text style={styles.muted}>
              {participant.status} / {participant.participantType}
            </Text>
          </View>
          <View style={styles.participantActions}>
            <Text style={styles.badge}>{participant.role}</Text>
            {canManageLobby && participant.userId !== currentUserId && participant.status === "JOINED" ? (
              <DangerButton label="Kick" onPress={() => onKick(participant.userId)} compact />
            ) : null}
          </View>
        </View>
      ))}
    </View>
  );
}

function RoomSettingsEditor({
  settings,
  activePlayerCount,
  onSave,
}: {
  settings: RoomResponse["settings"];
  activePlayerCount: number;
  onSave: (payload: UpdateRoomSettingsPayload) => void;
}) {
  const [writingStyle, setWritingStyle] = useState<WritingStyle>(settings.writingStyle);
  const [language, setLanguage] = useState(settings.language);
  const [safetyMode, setSafetyMode] = useState<SafetyMode>(settings.safetyMode);
  const [visibility, setVisibility] = useState<RoomVisibility>(settings.visibility);
  const [maxPlayers, setMaxPlayers] = useState(String(settings.maxPlayers));
  const [turnLimit, setTurnLimit] = useState(String(settings.turnLimit));
  const [turnTimeoutSeconds, setTurnTimeoutSeconds] = useState(String(settings.turnTimeoutSeconds));

  useEffect(() => {
    setWritingStyle(settings.writingStyle);
    setLanguage(settings.language);
    setSafetyMode(settings.safetyMode);
    setVisibility(settings.visibility);
    setMaxPlayers(String(settings.maxPlayers));
    setTurnLimit(String(settings.turnLimit));
    setTurnTimeoutSeconds(String(settings.turnTimeoutSeconds));
  }, [
    settings.language,
    settings.maxPlayers,
    settings.safetyMode,
    settings.turnLimit,
    settings.turnTimeoutSeconds,
    settings.visibility,
    settings.writingStyle,
  ]);

  const minimumPlayers = Math.max(2, activePlayerCount);
  const parsedMaxPlayers = boundedNumber(maxPlayers, minimumPlayers, 8, settings.maxPlayers);
  const parsedTurnLimit = boundedNumber(turnLimit, 1, 30, settings.turnLimit);
  const parsedTimeout = boundedNumber(turnTimeoutSeconds, 10, 300, settings.turnTimeoutSeconds);

  return (
    <View style={styles.panel}>
      <Text style={styles.sectionTitle}>Room Settings</Text>
      <GroupedOptionRow
        label="Writing style"
        groups={WRITING_STYLE_GROUPS}
        value={writingStyle}
        onChange={setWritingStyle}
        formatter={styleLabel}
      />
      <OptionRow label="Safety" options={SAFETY_MODES} value={safetyMode} onChange={setSafetyMode} />
      <OptionRow label="Visibility" options={VISIBILITIES} value={visibility} onChange={setVisibility} />
      <Field label="Language" value={language} onChangeText={setLanguage} autoCapitalize="none" />
      <Field label="Max players" value={maxPlayers} onChangeText={setMaxPlayers} keyboardType="number-pad" />
      <Field label="Turn limit" value={turnLimit} onChangeText={setTurnLimit} keyboardType="number-pad" />
      <Field
        label="Turn timeout seconds"
        value={turnTimeoutSeconds}
        onChangeText={setTurnTimeoutSeconds}
        keyboardType="number-pad"
      />
      <Text style={styles.muted}>
        {parsedMaxPlayers} players / {parsedTurnLimit} turns / {parsedTimeout}s timeout
      </Text>
      <PrimaryButton
        label="Save settings"
        onPress={() =>
          onSave({
            writingStyle,
            language: language.trim() || settings.language,
            safetyMode,
            maxPlayers: parsedMaxPlayers,
            turnLimit: parsedTurnLimit,
            turnTimeoutSeconds: parsedTimeout,
            visibility,
          })
        }
      />
    </View>
  );
}

function SettingsScreen({
  profile,
  apiBaseUrl,
  onBack,
  onRefresh,
  onSave,
  onSignOut,
}: {
  profile: MeResponse | null;
  apiBaseUrl: string;
  onBack: () => void;
  onRefresh: () => void;
  onSave: (payload: { displayName: string; avatarUrl?: string | null; favoriteStyle?: string | null }) => void;
  onSignOut: () => void;
}) {
  const [displayName, setDisplayName] = useState(profile?.displayName ?? "");
  const [avatarUrl, setAvatarUrl] = useState(profile?.avatarUrl ?? "");
  const [favoriteStyle, setFavoriteStyle] = useState(profile?.favoriteStyle ?? "");

  useEffect(() => {
    setDisplayName(profile?.displayName ?? "");
    setAvatarUrl(profile?.avatarUrl ?? "");
    setFavoriteStyle(profile?.favoriteStyle ?? "");
  }, [profile?.displayName, profile?.avatarUrl, profile?.favoriteStyle]);

  return (
    <View style={styles.stack}>
      <View style={styles.panel}>
        <Text style={styles.sectionTitle}>Settings</Text>
        <Text style={styles.muted}>{apiBaseUrl}</Text>
        <View style={styles.buttonRow}>
          <SecondaryButton label="Back" onPress={onBack} />
          <SecondaryButton label="Refresh" onPress={onRefresh} />
          <SecondaryButton label="Sign out" onPress={onSignOut} />
        </View>
      </View>

      <View style={styles.panel}>
        <Text style={styles.sectionTitle}>Profile</Text>
        {profile ? (
          <>
            <Text style={styles.muted}>{profile.email}</Text>
            <Text style={styles.muted}>
              {profile.status} / {profile.role}
            </Text>
          </>
        ) : (
          <Text style={styles.muted}>Profile has not loaded yet.</Text>
        )}
        <Field label="Display name" value={displayName} onChangeText={setDisplayName} />
        <Field label="Avatar URL" value={avatarUrl} onChangeText={setAvatarUrl} autoCapitalize="none" />
        <Field label="Favorite style" value={favoriteStyle} onChangeText={setFavoriteStyle} />
        <PrimaryButton
          label="Save profile"
          onPress={() =>
            onSave({
              displayName: displayName.trim(),
              avatarUrl: avatarUrl.trim() || null,
              favoriteStyle: favoriteStyle.trim() || null,
            })
          }
          disabled={!displayName.trim()}
        />
      </View>
    </View>
  );
}

function GameScreen({
  game,
  room,
  currentUserId,
  suggestion,
  voteResults,
  votedCategories,
  isGenerating,
  onBack,
  onRefresh,
  onSubmitWord,
  onRandomWord,
  onUseSuggestion,
  onSkipExpired,
  onRefreshResults,
  onSubmitVote,
  onShareStory,
}: {
  game: GameResponse;
  room: RoomResponse | null;
  currentUserId: string;
  suggestion: RandomWordSuggestionResponse | null;
  voteResults: VoteResultsResponse | null;
  votedCategories: VoteCategory[];
  isGenerating: boolean;
  onBack: () => void;
  onRefresh: () => void;
  onSubmitWord: (word: string) => void;
  onRandomWord: () => void;
  onUseSuggestion: () => string;
  onSkipExpired: () => void;
  onRefreshResults: () => void;
  onSubmitVote: (category: VoteCategory, targetId: string) => void;
  onShareStory: () => void;
}) {
  const [word, setWord] = useState("");
  const [now, setNow] = useState(() => Date.now());
  const currentPlayer = room?.participants.find((participant) => participant.userId === game.currentTurn.playerUserId) ?? null;
  const isBotTurn = currentPlayer?.participantType === "BOT";
  const isMyTurn = game.status === "ACTIVE" && game.currentTurn.playerUserId === currentUserId && !isBotTurn;
  const currentPlayerName = playerName(game.currentTurn.playerUserId, room);
  const turnLabel = isMyTurn ? "Your turn" : isBotTurn ? `${currentPlayerName} turn` : `Waiting for ${currentPlayerName}`;
  const wordValidation = validateOneWord(word);
  const canSubmitWord = isMyTurn && wordValidation.valid;
  const turnExpiresIn = game.status === "ACTIVE" ? formatTimeRemaining(game.currentTurn.expiresAt, now) : "";

  useEffect(() => {
    if (game.status !== "ACTIVE") {
      return;
    }
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, [game.status, game.currentTurn.turnId]);

  function submit() {
    const trimmedWord = word.trim();
    if (!wordValidation.valid) {
      return;
    }
    onSubmitWord(trimmedWord);
    setWord("");
  }

  function useSuggestion() {
    const suggestedWord = onUseSuggestion();
    if (suggestedWord) {
      setWord(suggestedWord);
    }
  }

  return (
    <View style={styles.stack}>
      <View style={styles.panel}>
        <View style={styles.rowBetween}>
          <View>
            <Text style={styles.sectionTitle}>Game</Text>
            <Text style={styles.muted}>
              {game.status} / turn {game.currentTurnNumber} of {game.turnLimit}
            </Text>
            {turnExpiresIn ? <Text style={styles.muted}>Turn timer: {turnExpiresIn}</Text> : null}
          </View>
          <Text style={[styles.statusPill, isMyTurn && styles.myTurnPill]}>{turnLabel}</Text>
        </View>
        <View style={styles.buttonRow}>
          <SecondaryButton label="Lobby" onPress={onBack} />
          <SecondaryButton label="Refresh" onPress={onRefresh} />
          {game.status === "ACTIVE" ? <SecondaryButton label="Skip expired" onPress={onSkipExpired} /> : null}
          {game.status === "FINISHED" ? <SecondaryButton label="Share story" onPress={onShareStory} /> : null}
        </View>
      </View>

      {game.status === "ACTIVE" ? (
        <View style={styles.panel}>
          <Text style={styles.sectionTitle}>Word</Text>
          <Text style={styles.muted}>
            {isMyTurn ? "Submit exactly one word to push the story forward." : `${currentPlayerName} is choosing a word.`}
          </Text>
          {isGenerating ? (
            <View style={styles.aiStatus}>
              <ActivityIndicator color="#0f766e" />
              <Text style={styles.aiStatusText}>AI is writing the next sentence.</Text>
            </View>
          ) : null}
          {isBotTurn ? (
            <View style={styles.aiStatus}>
              <ActivityIndicator color="#0f766e" />
              <Text style={styles.aiStatusText}>{currentPlayerName} is choosing a word...</Text>
            </View>
          ) : null}
          {suggestion && !isBotTurn ? (
            <Pressable style={styles.suggestion} onPress={useSuggestion}>
              <Text style={styles.suggestionWord}>{suggestion.word}</Text>
              <Text style={styles.muted}>Tap to use suggestion</Text>
            </Pressable>
          ) : null}
          {!isBotTurn ? (
            <>
              <Field label="One word" value={word} onChangeText={setWord} autoCapitalize="none" />
              {wordValidation.message ? <Text style={styles.validationText}>{wordValidation.message}</Text> : null}
              <View style={styles.buttonRow}>
                <PrimaryButton label="Submit word" onPress={submit} disabled={!canSubmitWord} />
                <SecondaryButton label="Random word" onPress={onRandomWord} disabled={!isMyTurn} />
              </View>
            </>
          ) : null}
        </View>
      ) : null}

      {game.status === "VOTING" || game.status === "FINISHED" ? (
        <VotingPanel
          game={game}
          room={room}
          voteResults={voteResults}
          votedCategories={votedCategories}
          onRefreshResults={onRefreshResults}
          onSubmitVote={onSubmitVote}
        />
      ) : null}

      <View style={styles.panel}>
        <Text style={styles.sectionTitle}>Story</Text>
        {game.storySegments.map((segment) => (
          <View key={segment.segmentId} style={styles.storySegment}>
            <Text style={styles.storyMeta}>
              {segment.turnNumber === null ? "Opening" : `Turn ${segment.turnNumber}`}
            </Text>
            <Text style={styles.storyText}>
              {renderableStoryText(segment).map((part, index) => (
                <Text key={`${segment.segmentId}-${index}`} style={part.bold ? styles.storyPlayedWord : undefined}>
                  {part.text}
                </Text>
              ))}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}

function VotingPanel({
  game,
  room,
  voteResults,
  votedCategories,
  onRefreshResults,
  onSubmitVote,
}: {
  game: GameResponse;
  room: RoomResponse | null;
  voteResults: VoteResultsResponse | null;
  votedCategories: VoteCategory[];
  onRefreshResults: () => void;
  onSubmitVote: (category: VoteCategory, targetId: string) => void;
}) {
  const playerTargets = room?.participants.filter((participant) => participant.status === "JOINED") ?? [];
  const storyTargets = game.storySegments.filter((segment) => segment.authorUserId !== null);
  const resultsByCategory = new Map(voteResults?.categories.map((category) => [category.category, category.results]) ?? []);

  return (
    <View style={styles.panel}>
      <View style={styles.rowBetween}>
        <View>
          <Text style={styles.sectionTitle}>{game.status === "FINISHED" ? "Final Results" : "Voting"}</Text>
          <Text style={styles.muted}>
            {game.status === "FINISHED" ? "The round is complete." : "Choose one target in each category."}
          </Text>
        </View>
        <SecondaryButton label="Results" onPress={onRefreshResults} />
      </View>

      {VOTE_CATEGORIES.map((category) => {
        const categoryResults = resultsByCategory.get(category) ?? [];
        const hasVoted = votedCategories.includes(category);
        const targetsPlayer = categoryTargetsPlayer(category);
        const targetOptions = targetsPlayer
          ? playerTargets.map((participant) => ({
              id: participant.userId,
              label: participant.displayName,
              detail: participant.role,
            }))
          : storyTargets.map((segment) => ({
              id: segment.segmentId,
              label: segment.turnNumber === null ? "Opening" : `Turn ${segment.turnNumber}`,
              detail: segment.content,
            }));

        return (
          <View key={category} style={styles.voteCategory}>
            <Text style={styles.voteTitle}>{categoryLabel(category)}</Text>
            {categoryResults.length > 0 ? (
              <View style={styles.resultList}>
                {categoryResults.slice(0, 3).map((result) => (
                  <Text key={`${category}-${result.targetUserId ?? result.targetStorySegmentId}`} style={styles.resultText}>
                    {targetLabel(result.targetUserId ?? result.targetStorySegmentId ?? "", targetsPlayer, room, game)}:{" "}
                    {result.voteCount}
                  </Text>
                ))}
              </View>
            ) : (
              <Text style={styles.muted}>No votes yet.</Text>
            )}

            {game.status === "VOTING" ? (
              hasVoted ? (
                <Text style={styles.submittedText}>Vote submitted</Text>
              ) : (
                <View style={styles.targetList}>
                  {targetOptions.map((target) => (
                    <Pressable
                      key={`${category}-${target.id}`}
                      style={styles.targetButton}
                      onPress={() => onSubmitVote(category, target.id)}
                    >
                      <Text style={styles.targetTitle}>{target.label}</Text>
                      <Text numberOfLines={2} style={styles.muted}>
                        {target.detail}
                      </Text>
                    </Pressable>
                  ))}
                </View>
              )
            ) : null}
          </View>
        );
      })}
    </View>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detailItem}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={styles.detailValue}>{value}</Text>
    </View>
  );
}

function Field(props: {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  autoCapitalize?: "none" | "sentences" | "words" | "characters";
  keyboardType?: "default" | "email-address" | "number-pad";
  secureTextEntry?: boolean;
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{props.label}</Text>
      <TextInput style={styles.input} {...props} placeholderTextColor="#8a9388" />
    </View>
  );
}

function OptionRow<TValue extends string>({
  label,
  options,
  value,
  onChange,
  formatter = (option) => option,
}: {
  label: string;
  options: readonly TValue[];
  value: TValue;
  onChange: (value: TValue) => void;
  formatter?: (value: TValue) => string;
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.optionGrid}>
        {options.map((option) => (
          <Pressable
            key={option}
            style={[styles.optionButton, option === value && styles.optionButtonActive]}
            onPress={() => onChange(option)}
          >
            <Text style={[styles.optionButtonText, option === value && styles.optionButtonTextActive]}>
              {formatter(option)}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

function GroupedOptionRow<TValue extends string>({
  label,
  groups,
  value,
  onChange,
  formatter = (option) => option,
}: {
  label: string;
  groups: readonly { label: string; options: readonly TValue[] }[];
  value: TValue;
  onChange: (value: TValue) => void;
  formatter?: (value: TValue) => string;
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      {groups.map((group) => (
        <View key={group.label} style={styles.optionGroup}>
          <Text style={styles.optionGroupTitle}>{group.label}</Text>
          <View style={styles.optionGrid}>
            {group.options.map((option) => (
              <Pressable
                key={option}
                style={[styles.optionButton, option === value && styles.optionButtonActive]}
                onPress={() => onChange(option)}
              >
                <Text style={[styles.optionButtonText, option === value && styles.optionButtonTextActive]}>
                  {formatter(option)}
                </Text>
              </Pressable>
            ))}
          </View>
        </View>
      ))}
    </View>
  );
}

function PrimaryButton({ label, onPress, disabled }: { label: string; onPress: () => void; disabled?: boolean }) {
  return (
    <Pressable style={[styles.primaryButton, disabled && styles.disabledButton]} onPress={onPress} disabled={disabled}>
      <Text style={styles.primaryButtonText}>{label}</Text>
    </Pressable>
  );
}

function SecondaryButton({ label, onPress, disabled }: { label: string; onPress: () => void; disabled?: boolean }) {
  return (
    <Pressable style={[styles.secondaryButton, disabled && styles.disabledButton]} onPress={onPress} disabled={disabled}>
      <Text style={styles.secondaryButtonText}>{label}</Text>
    </Pressable>
  );
}

function DangerButton({
  label,
  onPress,
  compact,
}: {
  label: string;
  onPress: () => void;
  compact?: boolean;
}) {
  return (
    <Pressable style={[styles.dangerButton, compact && styles.compactButton]} onPress={onPress}>
      <Text style={styles.dangerButtonText}>{label}</Text>
    </Pressable>
  );
}

function SegmentButton({ active, label, onPress }: { active: boolean; label: string; onPress: () => void }) {
  return (
    <Pressable style={[styles.segmentButton, active && styles.segmentButtonActive]} onPress={onPress}>
      <Text style={[styles.segmentButtonText, active && styles.segmentButtonTextActive]}>{label}</Text>
    </Pressable>
  );
}

function Banner({ tone, message }: { tone: "error" | "neutral"; message: string }) {
  return (
    <View style={[styles.banner, tone === "error" ? styles.errorBanner : styles.neutralBanner]}>
      <Text style={styles.bannerText}>{message}</Text>
    </View>
  );
}

function readableError(caught: unknown) {
  if (caught instanceof ApiError) {
    const body = caught.body as { errorCode?: string; message?: string } | null;
    return body?.message || body?.errorCode || "Request failed. Check the backend and try again.";
  }
  if (caught instanceof Error) {
    return caught.message;
  }
  return "Something went wrong.";
}

function isAuthExpired(caught: unknown) {
  if (!(caught instanceof ApiError)) {
    return false;
  }
  const body = caught.body as { errorCode?: string } | null;
  return caught.status === 401 || body?.errorCode === "AUTH_REQUIRED";
}

function shortId(id: string) {
  return id.length <= 8 ? id : id.slice(0, 8);
}

function boundedNumber(value: string, min: number, max: number, fallback: number) {
  const parsed = Number.parseInt(value, 10);
  if (Number.isNaN(parsed)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, parsed));
}

function validateOneWord(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return { valid: false, message: "" };
  }
  if (trimmed.split(/\s+/).length > 1) {
    return { valid: false, message: "Use exactly one word." };
  }
  return { valid: true, message: "" };
}

function formatTimeRemaining(expiresAt: string, now: number) {
  const remainingMs = new Date(expiresAt).getTime() - now;
  if (!Number.isFinite(remainingMs)) {
    return "";
  }
  if (remainingMs <= 0) {
    return "expired";
  }
  const totalSeconds = Math.ceil(remainingMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes > 0 ? `${minutes}:${seconds.toString().padStart(2, "0")}` : `${seconds}s`;
}

function isRoomResponse(payload: unknown): payload is RoomResponse {
  return typeof payload === "object" && payload !== null && "roomId" in payload && "participants" in payload;
}

function isGameResponse(payload: unknown): payload is GameResponse {
  return typeof payload === "object" && payload !== null && "gameId" in payload && "storySegments" in payload;
}

function isVoteResultsResponse(payload: unknown): payload is VoteResultsResponse {
  return typeof payload === "object" && payload !== null && "gameId" in payload && "categories" in payload;
}

function categoryTargetsPlayer(category: VoteCategory) {
  return category === "BEST_SABOTAGE" || category === "MVP_PLAYER";
}

function categoryLabel(category: VoteCategory) {
  switch (category) {
    case "FUNNIEST_WORD":
      return "Funniest Word";
    case "BEST_SABOTAGE":
      return "Best Sabotage";
    case "WEIRDEST_TWIST":
      return "Weirdest Twist";
    case "BEST_AI_SENTENCE":
      return "Best AI Sentence";
    case "MVP_PLAYER":
      return "MVP Player";
  }
}

function targetLabel(targetId: string, targetsPlayer: boolean, room: RoomResponse | null, game: GameResponse) {
  if (targetsPlayer) {
    return playerName(targetId, room);
  }
  const segment = game.storySegments.find((storySegment) => storySegment.segmentId === targetId);
  return segment?.turnNumber === null ? "Opening" : segment?.turnNumber ? `Turn ${segment.turnNumber}` : shortId(targetId);
}

function playerName(userId: string, room: RoomResponse | null) {
  return room?.participants.find((participant) => participant.userId === userId)?.displayName ?? shortId(userId);
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#f6f7fb",
  },
  content: {
    gap: 14,
    padding: 20,
    paddingBottom: 32,
  },
  header: {
    gap: 4,
    paddingTop: 10,
  },
  kicker: {
    color: "#a8422d",
    fontSize: 13,
    fontWeight: "800",
    letterSpacing: 0,
    textTransform: "uppercase",
  },
  title: {
    color: "#1f2a24",
    fontSize: 34,
    fontWeight: "800",
    letterSpacing: 0,
  },
  stack: {
    gap: 12,
  },
  panel: {
    backgroundColor: "#ffffff",
    borderColor: "#d8dde6",
    borderRadius: 8,
    borderWidth: 1,
    gap: 12,
    padding: 14,
  },
  sectionTitle: {
    color: "#1f2a24",
    fontSize: 18,
    fontWeight: "800",
    letterSpacing: 0,
  },
  muted: {
    color: "#626b72",
    fontSize: 14,
    lineHeight: 20,
  },
  field: {
    gap: 6,
  },
  label: {
    color: "#43505a",
    fontSize: 13,
    fontWeight: "700",
  },
  optionGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  optionGroup: {
    gap: 8,
  },
  optionGroupTitle: {
    color: "#626b72",
    fontSize: 12,
    fontWeight: "800",
    textTransform: "uppercase",
  },
  optionButton: {
    backgroundColor: "#f8fafc",
    borderColor: "#cbd5e1",
    borderRadius: 8,
    borderWidth: 1,
    minHeight: 38,
    justifyContent: "center",
    paddingHorizontal: 10,
  },
  optionButtonActive: {
    backgroundColor: "#d7ece7",
    borderColor: "#0f766e",
  },
  optionButtonText: {
    color: "#43505a",
    fontSize: 13,
    fontWeight: "800",
  },
  optionButtonTextActive: {
    color: "#0f5f58",
  },
  input: {
    backgroundColor: "#f8fafc",
    borderColor: "#cbd5e1",
    borderRadius: 8,
    borderWidth: 1,
    color: "#1f2a24",
    fontSize: 16,
    minHeight: 48,
    paddingHorizontal: 12,
  },
  segmented: {
    backgroundColor: "#e7edf3",
    borderRadius: 8,
    flexDirection: "row",
    padding: 4,
  },
  segmentButton: {
    alignItems: "center",
    borderRadius: 6,
    flex: 1,
    minHeight: 40,
    justifyContent: "center",
  },
  segmentButtonActive: {
    backgroundColor: "#ffffff",
  },
  segmentButtonText: {
    color: "#667064",
    fontWeight: "800",
  },
  segmentButtonTextActive: {
    color: "#1f2a24",
  },
  primaryButton: {
    alignItems: "center",
    backgroundColor: "#0f766e",
    borderRadius: 8,
    minHeight: 48,
    justifyContent: "center",
    paddingHorizontal: 14,
  },
  primaryButtonText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "800",
  },
  secondaryButton: {
    alignItems: "center",
    borderColor: "#b7aa9a",
    borderRadius: 8,
    borderWidth: 1,
    minHeight: 42,
    justifyContent: "center",
    paddingHorizontal: 12,
  },
  secondaryButtonText: {
    color: "#33443a",
    fontWeight: "800",
  },
  dangerButton: {
    alignItems: "center",
    backgroundColor: "#a8422d",
    borderRadius: 8,
    minHeight: 42,
    justifyContent: "center",
    paddingHorizontal: 12,
  },
  dangerButtonText: {
    color: "#ffffff",
    fontWeight: "800",
  },
  compactButton: {
    minHeight: 34,
    paddingHorizontal: 10,
  },
  buttonRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  rowBetween: {
    alignItems: "center",
    flexDirection: "row",
    gap: 12,
    justifyContent: "space-between",
  },
  detailGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  detailItem: {
    backgroundColor: "#f8fafc",
    borderColor: "#d8dde6",
    borderRadius: 8,
    borderWidth: 1,
    minWidth: 130,
    padding: 10,
  },
  detailLabel: {
    color: "#626b72",
    fontSize: 12,
    fontWeight: "700",
  },
  detailValue: {
    color: "#1f2a24",
    fontSize: 15,
    fontWeight: "800",
    marginTop: 2,
  },
  joinRow: {
    flexDirection: "row",
    gap: 8,
  },
  joinInput: {
    flex: 1,
  },
  joinButton: {
    alignItems: "center",
    backgroundColor: "#a8422d",
    borderRadius: 8,
    justifyContent: "center",
    minWidth: 76,
  },
  joinButtonText: {
    color: "#ffffff",
    fontWeight: "800",
  },
  participantActions: {
    alignItems: "flex-end",
    gap: 8,
  },
  emptyState: {
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderColor: "#d8dde6",
    borderRadius: 8,
    borderStyle: "dashed",
    borderWidth: 1,
    gap: 4,
    padding: 18,
  },
  emptyTitle: {
    color: "#1f2a24",
    fontSize: 16,
    fontWeight: "800",
  },
  listItem: {
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderColor: "#d8dde6",
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: "row",
    justifyContent: "space-between",
    gap: 12,
    padding: 14,
  },
  listTitle: {
    color: "#1f2a24",
    fontSize: 17,
    fontWeight: "800",
  },
  badge: {
    backgroundColor: "#e7edf3",
    borderRadius: 6,
    color: "#33443a",
    fontSize: 12,
    fontWeight: "800",
    overflow: "hidden",
    paddingHorizontal: 8,
    paddingVertical: 5,
  },
  statusPill: {
    backgroundColor: "#e7edf3",
    borderRadius: 6,
    color: "#33443a",
    flexShrink: 1,
    fontSize: 12,
    fontWeight: "800",
    overflow: "hidden",
    paddingHorizontal: 8,
    paddingVertical: 6,
    textAlign: "center",
  },
  myTurnPill: {
    backgroundColor: "#d7ece7",
    color: "#0f5f58",
  },
  suggestion: {
    backgroundColor: "#fff7ed",
    borderColor: "#fed7aa",
    borderRadius: 8,
    borderWidth: 1,
    gap: 2,
    padding: 12,
  },
  suggestionWord: {
    color: "#9a3412",
    fontSize: 20,
    fontWeight: "800",
  },
  validationText: {
    color: "#a8422d",
    fontSize: 13,
    fontWeight: "800",
  },
  aiStatus: {
    alignItems: "center",
    backgroundColor: "#d7ece7",
    borderColor: "#9ccfc4",
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: "row",
    gap: 10,
    padding: 12,
  },
  aiStatusText: {
    color: "#0f5f58",
    flex: 1,
    fontSize: 14,
    fontWeight: "800",
  },
  storySegment: {
    borderLeftColor: "#0f766e",
    borderLeftWidth: 3,
    gap: 4,
    paddingLeft: 10,
    paddingVertical: 6,
  },
  storyMeta: {
    color: "#626b72",
    fontSize: 12,
    fontWeight: "800",
    textTransform: "uppercase",
  },
  storyText: {
    color: "#1f2a24",
    fontSize: 16,
    lineHeight: 23,
  },
  storyPlayedWord: {
    fontWeight: "800",
  },
  voteCategory: {
    borderColor: "#e7edf3",
    borderRadius: 8,
    borderWidth: 1,
    gap: 8,
    padding: 12,
  },
  voteTitle: {
    color: "#1f2a24",
    fontSize: 16,
    fontWeight: "800",
  },
  resultList: {
    gap: 4,
  },
  resultText: {
    color: "#43505a",
    fontSize: 14,
    fontWeight: "700",
    lineHeight: 20,
  },
  targetList: {
    gap: 8,
  },
  targetButton: {
    backgroundColor: "#f8fafc",
    borderColor: "#cbd5e1",
    borderRadius: 8,
    borderWidth: 1,
    gap: 3,
    padding: 10,
  },
  targetTitle: {
    color: "#0f5f58",
    fontSize: 15,
    fontWeight: "800",
  },
  submittedText: {
    color: "#0f5f58",
    fontSize: 14,
    fontWeight: "800",
  },
  disabledButton: {
    opacity: 0.45,
  },
  banner: {
    borderRadius: 8,
    padding: 12,
  },
  errorBanner: {
    backgroundColor: "#f5d7d0",
  },
  neutralBanner: {
    backgroundColor: "#d7ece7",
  },
  bannerText: {
    color: "#1f2a24",
    fontWeight: "700",
    lineHeight: 20,
  },
});
