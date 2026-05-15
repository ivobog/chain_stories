# Play With Bot Execution Plan

Date: 2026-05-15

Source documents analyzed:

- `C:/Users/Ivica/Downloads/chain_stories_play_with_bot_srs.docx`
- `C:/Users/Ivica/Downloads/chain_stories_play_with_bot_sdd.docx`

Repository baseline reviewed:

- `C:/Users/Ivica/Documents/New project/backend`
- `C:/Users/Ivica/Documents/New project/mobile`

## Goal

Deliver two additive, backward-compatible features:

1. `Play with Bot`: a one-click private two-player game between one authenticated human and one backend-controlled bot.
2. `Played word highlight`: store the submitted word per story segment and render that word in bold on mobile without mutating stored story text.

## Current Baseline

The repository already has most of the required building blocks:

- Spring Boot backend with `RoomService`, `GameService`, auth, realtime publishing, Flyway migrations, and AI services.
- Expo React Native mobile app with a typed API client and realtime subscriptions.
- Existing game flow already supports room creation, joining, starting games, random word suggestions, word submission, story generation, and realtime refresh.

The main gaps relative to the SRS/SDD are:

- No `UserAccountType` on `User`.
- No `GameMode` on `Room`.
- No `participantType` in `RoomParticipantResponse`.
- No `playedWord` metadata in `StorySegment` or `StorySegmentResponse`.
- No dedicated `POST /api/v1/games/play-with-bot` endpoint.
- No backend bot player service or after-commit bot turn automation.
- No mobile `Play with Bot` action or bot-turn-specific UI.
- No client-side played-word bold renderer.

## Delivery Strategy

Ship this in six phases so the database and contracts land before async automation and UI changes.

### Phase 1: Schema and Domain Foundations

Objective: add the minimum data model needed without breaking current flows.

Backend tasks:

- Add `V13__play_with_bot_and_played_word.sql` under `backend/src/main/resources/db/migration`.
- Add `UserAccountType` enum under `backend/src/main/java/com/chainreaction/user/domain`.
- Add `GameMode` enum under `backend/src/main/java/com/chainreaction/room/domain`.
- Optionally add `SubmissionSource` enum under `backend/src/main/java/com/chainreaction/game/domain`; if scope needs to stay smaller, defer this field until Phase 4.
- Update [`User.java`](C:/Users/Ivica/Documents/New%20project/backend/src/main/java/com/chainreaction/user/domain/User.java) to store `accountType`, default `HUMAN`, plus helper methods like `isHuman()` and `isBot()`.
- Update [`Room.java`](C:/Users/Ivica/Documents/New%20project/backend/src/main/java/com/chainreaction/room/domain/Room.java) to store `gameMode`, default `MULTIPLAYER`.
- Update [`StorySegment.java`](C:/Users/Ivica/Documents/New%20project/backend/src/main/java/com/chainreaction/game/domain/StorySegment.java) to store nullable `playedWord` and `playedWordNormalized`, plus `submissionSource` if included.

Acceptance for this phase:

- Application starts with the new migration.
- Existing room/game flows remain functional with defaulted values.
- Existing story segments remain readable with null played-word fields.

### Phase 2: Security and API Contract Prep

Objective: introduce the safe bot account model and additive response fields before bot gameplay is added.

Backend tasks:

- Update [`AuthService.java`](C:/Users/Ivica/Documents/New%20project/backend/src/main/java/com/chainreaction/auth/service/AuthService.java) so only `HUMAN` users can authenticate.
- Add a `BotPlayerService` under a new package such as `com.chainreaction.bot.service` or `com.chainreaction.game.service`.
- Use one controlled backend bot user for v1, for example `storybot@system.local`.
- Ensure the bot has no usable human login path.
- Extend [`RoomParticipantResponse.java`](C:/Users/Ivica/Documents/New%20project/backend/src/main/java/com/chainreaction/room/api/RoomParticipantResponse.java) with `participantType`.
- Extend [`StorySegmentResponse.java`](C:/Users/Ivica/Documents/New%20project/backend/src/main/java/com/chainreaction/game/api/StorySegmentResponse.java) with nullable `playedWord` and `playedWordNormalized`.
- Keep new fields additive and nullable so older consumers do not break.

Acceptance for this phase:

- BOT users are rejected by login and refresh flows.
- Room and game APIs now expose enough metadata for the mobile client to distinguish bot players and played words.

### Phase 3: Play-With-Bot Room Creation

Objective: create and start a bot game through a single authenticated endpoint using existing room/game services.

Backend tasks:

- Add `PlayWithBotRequest` and `PlayWithBotResponse` DTOs under `backend/src/main/java/com/chainreaction/game/api`.
- Add `PlayWithBotController` with `POST /api/v1/games/play-with-bot`.
- Add `PlayWithBotService` to orchestrate:
  - require authenticated human user
  - get or create StoryBot
  - create a private room with `maxPlayers = 2`
  - set `gameMode = PLAY_WITH_BOT`
  - add human as host and bot as joined participant
  - start the game through `GameService`
  - return room plus game state
- Add focused helpers to [`RoomService.java`](C:/Users/Ivica/Documents/New%20project/backend/src/main/java/com/chainreaction/room/service/RoomService.java) rather than duplicating room logic elsewhere.
- Ensure bot rooms are not discoverable through public room flows.

Recommended service seams:

- `RoomService.createPlayWithBotRoom(...)`
- `RoomService.addParticipant(...)` or `RoomService.addBotParticipant(...)`
- `GameService.startGameInternal(...)` if the existing public start path needs a reusable host-safe internal variant

Acceptance for this phase:

- A logged-in user can create a private game with exactly two participants: self plus StoryBot.
- The returned room/game payload is enough for immediate mobile navigation to the active game.

### Phase 4: Bot Turn Automation and Shared Submission Flow

Objective: make bot turns run through the same backend engine as human turns, after commit and with idempotent guards.

Backend tasks:

- Refactor [`GameService.java`](C:/Users/Ivica/Documents/New%20project/backend/src/main/java/com/chainreaction/game/service/GameService.java) so:
  - `submitWord(...)` remains the public human entry point
  - shared submission logic moves into an internal method
  - a new internal bot-specific submission path can call the same validation, moderation, AI generation, registry, persistence, and realtime code
- During segment creation, persist `playedWord` and `playedWordNormalized`.
- Publish or trigger an internal turn-started event after the transaction commits.
- Add `BotTurnListener` using `@TransactionalEventListener(phase = AFTER_COMMIT)` and `@Async` if async execution is already configured or can be safely added.
- Add `BotTurnService` to:
  - reload the current turn
  - exit unless the turn is still active and owned by a bot
  - optionally wait for configured delay
  - call `WordSuggestionService`
  - retry suggestion a bounded number of times on moderation/validation failures
  - submit the word through the internal `GameService` path
- Add a locking or guarded reload strategy in [`GameTurnRepository.java`](C:/Users/Ivica/Documents/New%20project/backend/src/main/java/com/chainreaction/game/repository/GameTurnRepository.java) to prevent duplicate bot submissions.

Important design rule:

- Bot logic must never call the public REST endpoint from the backend. It should use service-to-service calls inside the same transaction boundary model described by the SDD.

Acceptance for this phase:

- When the current turn belongs to StoryBot, the backend auto-plays without polling.
- Duplicate turn-start processing does not create duplicate story segments.
- Human multiplayer still follows the same submit flow as before.

### Phase 5: Mobile Integration and UX

Objective: surface the new mode with minimal UI churn and keep gameplay understandable during bot turns.

Mobile tasks:

- Extend [`mobile/src/api/types.ts`](C:/Users/Ivica/Documents/New%20project/mobile/src/api/types.ts):
  - add `ParticipantType`
  - add `participantType` to `RoomParticipantResponse`
  - add `playedWord` and `playedWordNormalized` to `StorySegmentResponse`
  - add `PlayWithBot` request/response types if helpful
- Extend [`mobile/src/api/client.ts`](C:/Users/Ivica/Documents/New%20project/mobile/src/api/client.ts) with `playWithBot(payload)`.
- Update [`mobile/App.tsx`](C:/Users/Ivica/Documents/New%20project/mobile/App.tsx) to:
  - add a `Play with Bot` action near room creation/join flows
  - call the new endpoint
  - set `activeRoom` and `activeGame` from the returned payload
  - detect whether the current player is a bot
  - hide or disable word input on bot turns
  - show a status like `StoryBot is choosing a word...`
- Add a small pure helper for split-and-highlight logic instead of embedding regex-heavy logic inline in the screen.
- Render story text as nested `<Text>` fragments so only exact word matches become bold.

Recommended implementation split:

- Keep API contract changes in `mobile/src/api`.
- Put played-word splitting/highlight logic in a small helper module under `mobile/src` so it can be unit-tested separately from `App.tsx`.

Acceptance for this phase:

- Human turns look unchanged.
- Bot turns clearly communicate that input is unavailable.
- Story segments render the played word in bold only when there is a safe exact-word match.

### Phase 6: Regression and Release Hardening

Objective: prove the new mode works without destabilizing normal multiplayer.

Backend tests:

- Add auth coverage for rejecting BOT login.
- Add service/integration tests for `PlayWithBotService`.
- Add integration coverage for `POST /api/v1/games/play-with-bot`.
- Add bot-turn automation tests around duplicate events, inactive turns, and finished games.
- Add story segment persistence tests for `playedWord` fields.
- Keep or expand regression coverage for normal room creation, join, start, submit, and voting.

Mobile tests:

- API client test for `playWithBot`.
- Unit tests for the played-word split/highlight helper:
  - exact match
  - case-insensitive match if supported
  - punctuation boundaries
  - no match inside larger words such as `cat` in `cathedral`
- UI tests for hiding input during bot turns if there is already a test setup for `App.tsx`; otherwise keep this as a manual validation item for the first pass.

Manual verification:

1. Create a normal room and complete at least one human turn.
2. Start a Play with Bot game and observe StoryBot in participant state.
3. Submit a human word and confirm realtime updates still arrive.
4. Wait for a bot turn and confirm auto-play occurs once.
5. Confirm bold highlighting is visible in both the timeline and any final-story view that reuses segment rendering.

## Suggested Implementation Order

Use this exact order to minimize merge risk and incomplete contracts:

1. Database migration and enums.
2. Entity and response DTO updates.
3. Auth guard for non-human users.
4. Bot player service.
5. Play-with-bot endpoint and service.
6. `GameService` submit-flow refactor.
7. After-commit bot automation and idempotency protections.
8. Mobile API types and client method.
9. Mobile UX changes and played-word renderer.
10. Regression tests and release checklist.

## File-Level Worklist

Backend likely files:

- `backend/src/main/resources/db/migration/V13__play_with_bot_and_played_word.sql`
- `backend/src/main/java/com/chainreaction/user/domain/User.java`
- `backend/src/main/java/com/chainreaction/user/domain/UserAccountType.java`
- `backend/src/main/java/com/chainreaction/auth/service/AuthService.java`
- `backend/src/main/java/com/chainreaction/room/domain/Room.java`
- `backend/src/main/java/com/chainreaction/room/domain/GameMode.java`
- `backend/src/main/java/com/chainreaction/room/service/RoomService.java`
- `backend/src/main/java/com/chainreaction/room/api/RoomParticipantResponse.java`
- `backend/src/main/java/com/chainreaction/game/domain/StorySegment.java`
- `backend/src/main/java/com/chainreaction/game/api/StorySegmentResponse.java`
- `backend/src/main/java/com/chainreaction/game/service/GameService.java`
- `backend/src/main/java/com/chainreaction/game/repository/GameTurnRepository.java`
- `backend/src/main/java/com/chainreaction/game/api/PlayWithBotController.java`
- `backend/src/main/java/com/chainreaction/game/api/PlayWithBotRequest.java`
- `backend/src/main/java/com/chainreaction/game/api/PlayWithBotResponse.java`
- `backend/src/main/java/com/chainreaction/game/service/PlayWithBotService.java`
- `backend/src/main/java/com/chainreaction/game/service/BotPlayerService.java`
- `backend/src/main/java/com/chainreaction/game/service/BotTurnService.java`
- `backend/src/main/java/com/chainreaction/game/service/BotTurnListener.java`

Mobile likely files:

- `mobile/src/api/types.ts`
- `mobile/src/api/client.ts`
- `mobile/App.tsx`
- `mobile/src/...` new helper for story-word highlighting

Tests likely files:

- `backend/src/test/java/com/chainreaction/auth/...`
- `backend/src/test/java/com/chainreaction/game/...`
- `backend/src/test/java/com/chainreaction/realtime/...`
- `mobile/src/api/client.test.ts`
- `mobile/src/...` new helper test file

## Risks and Mitigations

### Risk 1: Duplicate bot submissions

Mitigation:

- Use after-commit event handling.
- Reload and lock the turn before bot submission.
- Exit unless turn status is still `ACTIVE` and owned by a bot.

### Risk 2: Breaking normal multiplayer contracts

Mitigation:

- Keep new response fields additive and nullable.
- Preserve existing endpoints.
- Run room/game regression tests before and after the bot path lands.

### Risk 3: Bot becomes host or leaks into normal room flows

Mitigation:

- Never use bot as transfer target.
- Close play-with-bot rooms when the human host leaves.
- Filter `PLAY_WITH_BOT` rooms out of any public/discovery logic.

### Risk 4: Highlighting false positives in story text

Mitigation:

- Match on safe boundaries, not substring search.
- Unit-test punctuation and embedded-word edge cases.
- Keep persisted story content plain text.

### Risk 5: `App.tsx` grows harder to maintain

Mitigation:

- Keep this feature additive but extract the highlight helper and any request helpers into small modules.
- If the screen becomes unwieldy during implementation, do a lightweight extraction of story rendering and action handlers as part of the same branch.

## Scope Recommendation

For the first implementation, include:

- `accountType`
- `gameMode`
- `participantType`
- `playedWord`
- `playedWordNormalized`
- one StoryBot identity
- one dedicated endpoint
- after-commit bot turn automation
- mobile bot-turn UX
- bold played-word rendering

For the first implementation, defer unless they come almost for free:

- custom bot personas
- richer bot realtime event types
- advanced analytics dashboards
- full demo autoplay

## Definition of Done

This feature is ready when all of the following are true:

- A logged-in human can start a private Play with Bot game from mobile.
- StoryBot appears as a real participant and never authenticates as a human.
- Bot turns auto-submit through backend service flow using `WordSuggestionService`.
- Story segments persist played-word metadata.
- Mobile renders the played word in bold without storing markup in story content.
- Normal multiplayer behavior remains unchanged.
- Automated tests cover the new backend flow, auth guardrails, and played-word matching.
