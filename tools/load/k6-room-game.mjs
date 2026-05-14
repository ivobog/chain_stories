import http from "k6/http";
import { check, fail, sleep } from "k6";

const BASE_URL = (__ENV.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");
const VUS = Number(__ENV.K6_VUS || "2");
const ITERATIONS = Number(__ENV.K6_ITERATIONS || String(VUS));

export const options = {
  vus: VUS,
  iterations: ITERATIONS,
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<1500"],
  },
};

export default function () {
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  const host = register(`load-host-${suffix}@example.com`, "Load Host");
  const player = register(`load-player-${suffix}@example.com`, "Load Player");

  const room = createRoom(host.accessToken);
  joinRoom(player.accessToken, room.roomCode);

  const started = startGame(host.accessToken, room.roomId);
  randomWord(host.accessToken, started.gameId);

  const afterFirstTurn = submitWord(host.accessToken, started.gameId, started.currentTurn.turnId, "spark");
  const afterSecondTurn = submitWord(
    player.accessToken,
    started.gameId,
    afterFirstTurn.currentTurn.turnId,
    "moon",
  );

  check(afterSecondTurn, {
    "game enters voting": (body) => body.status === "VOTING",
    "story has generated segments": (body) => body.storySegments.length >= 3,
  });

  const firstGeneratedSegment = afterSecondTurn.storySegments[1].segmentId;
  voteAllCategories(host, started.gameId, firstGeneratedSegment, player.userId);
  voteAllCategories(player, started.gameId, firstGeneratedSegment, host.userId);

  const results = getResults(host.accessToken, started.gameId);
  check(results, {
    "results include all categories": (body) => body.categories.length === 5,
  });

  sleep(1);
}

function register(email, displayName) {
  const response = postJson("/api/v1/auth/register", {
    email,
    password: "SecretPassword123!",
    displayName,
  });
  return expectJson(response, 201, "register");
}

function createRoom(token) {
  const response = postJson(
    "/api/v1/rooms",
    {
      writingStyle: "FUNNY",
      language: "en",
      safetyMode: "TEEN",
      maxPlayers: 2,
      turnLimit: 2,
      turnTimeoutSeconds: 30,
      visibility: "PRIVATE",
    },
    token,
  );
  return expectJson(response, 200, "create room");
}

function joinRoom(token, roomCode) {
  const response = postJson(`/api/v1/rooms/${roomCode}/join`, {}, token);
  expectJson(response, 200, "join room");
}

function startGame(token, roomId) {
  const response = postJson(`/api/v1/rooms/${roomId}/games/start`, {}, token);
  return expectJson(response, 200, "start game");
}

function randomWord(token, gameId) {
  const response = postJson(`/api/v1/games/${gameId}/random-word`, {}, token);
  expectJson(response, 200, "random word");
}

function submitWord(token, gameId, turnId, word) {
  const response = postJson(`/api/v1/games/${gameId}/turns/${turnId}/submit-word`, { word }, token);
  return expectJson(response, 200, `submit ${word}`);
}

function voteAllCategories(actor, gameId, storySegmentId, targetUserId) {
  submitVote(actor.accessToken, gameId, { category: "FUNNIEST_WORD", targetStorySegmentId: storySegmentId });
  submitVote(actor.accessToken, gameId, { category: "WEIRDEST_TWIST", targetStorySegmentId: storySegmentId });
  submitVote(actor.accessToken, gameId, { category: "BEST_AI_SENTENCE", targetStorySegmentId: storySegmentId });
  submitVote(actor.accessToken, gameId, { category: "BEST_SABOTAGE", targetUserId });
  submitVote(actor.accessToken, gameId, { category: "MVP_PLAYER", targetUserId });
}

function submitVote(token, gameId, body) {
  const response = postJson(`/api/v1/games/${gameId}/votes`, body, token);
  expectJson(response, 200, `vote ${body.category}`);
}

function getResults(token, gameId) {
  const response = http.get(`${BASE_URL}/api/v1/games/${gameId}/votes/results`, authParams(token));
  return expectJson(response, 200, "vote results");
}

function postJson(path, body, token) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body), authParams(token));
}

function authParams(token) {
  const headers = { "Content-Type": "application/json" };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return { headers };
}

function expectJson(response, expectedStatus, label) {
  const ok = check(response, {
    [`${label} status ${expectedStatus}`]: (res) => res.status === expectedStatus,
  });
  if (!ok) {
    fail(`${label} failed: expected ${expectedStatus}, got ${response.status} body=${response.body}`);
  }
  return response.json();
}
