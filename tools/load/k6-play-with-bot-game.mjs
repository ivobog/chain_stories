import http from "k6/http";
import { check, fail, sleep } from "k6";

const BASE_URL = (__ENV.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");
const VUS = Number(__ENV.K6_VUS || "1");
const ITERATIONS = Number(__ENV.K6_ITERATIONS || String(VUS));
const BOT_POLL_RETRIES = Number(__ENV.K6_BOT_POLL_RETRIES || "12");
const BOT_POLL_SLEEP_SECONDS = Number(__ENV.K6_BOT_POLL_SLEEP_SECONDS || "0.5");

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
  const player = register(`bot-host-${suffix}@example.com`, "Bot Host");

  const created = createPlayWithBotGame(player.accessToken);
  const gameId = created.game.gameId;
  const firstTurn = created.game.currentTurn;

  check(created.game, {
    "play-with-bot starts active": (game) => game.status === "ACTIVE",
    "play-with-bot starts with opening segment": (game) => game.storySegments.length === 1,
  });

  const afterHumanTurn = submitWord(player.accessToken, gameId, firstTurn.turnId, "fireman");

  check(afterHumanTurn, {
    "human submit advances to bot turn": (game) =>
      game.status === "ACTIVE"
      && game.currentTurnNumber === 2
      && game.storySegments.length === 2,
  });

  const finalState = awaitBotTurn(player.accessToken, gameId);

  check(finalState, {
    "bot turn completes game": (game) => game.status === "VOTING",
    "bot turn appends a story segment": (game) => game.storySegments.length === 3,
    "human segment stores played word": (game) => game.storySegments[1].playedWord === "fireman",
    "bot segment stores played word": (game) => Boolean(game.storySegments[2].playedWord),
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

function createPlayWithBotGame(token) {
  const response = postJson(
    "/api/v1/games/play-with-bot",
    {
      writingStyle: "FUNNY",
      language: "en",
      safetyMode: "TEEN",
      turnLimit: 2,
      turnTimeoutSeconds: 60,
    },
    token,
  );
  return expectJson(response, 201, "play with bot");
}

function submitWord(token, gameId, turnId, word) {
  const response = postJson(`/api/v1/games/${gameId}/turns/${turnId}/submit-word`, { word }, token);
  return expectJson(response, 200, `submit ${word}`);
}

function awaitBotTurn(token, gameId) {
  for (let attempt = 1; attempt <= BOT_POLL_RETRIES; attempt += 1) {
    const game = getGame(token, gameId);
    if (game.status === "VOTING" && game.storySegments.length >= 3) {
      return game;
    }
    sleep(BOT_POLL_SLEEP_SECONDS);
  }

  fail(`bot turn did not finish within ${BOT_POLL_RETRIES} polls`);
}

function getGame(token, gameId) {
  const response = http.get(`${BASE_URL}/api/v1/games/${gameId}`, authParams(token));
  return expectJson(response, 200, "get game");
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
