# Mobile Phase 9 Manual Checklist

Use this checklist before closing Phase 9. It is designed for two mobile clients or simulators against one local backend.

## Setup

1. Start infrastructure and backend.

```powershell
docker compose up -d
cd backend
mvn spring-boot:run
```

2. Start the mobile app.

```powershell
cd mobile
npm install
npm run start
```

3. In each mobile client, set the backend URL to an address reachable by that client.

- Android emulator: `http://10.0.2.2:8080`
- iOS simulator: `http://localhost:8080`
- Physical device: `http://<machine-lan-ip>:8080`

4. Run automated checks before manual testing.

```powershell
cd mobile
npm run test
npm run typecheck
```

## Happy Path

- Register or sign in as Player A.
- Register or sign in as Player B on the second client.
- Player A creates a private room with non-default settings.
- Player A uses `Share code`; verify the native share sheet opens and contains the room code.
- Player B opens `Join by code`, previews the room, and verifies host, status, capacity, style, safety, and joinability.
- Player B joins the room.
- Both clients show the same lobby participants.
- Player A edits lobby settings and saves them.
- Both clients refresh or receive updates and see the changed settings.
- Player A starts the game.
- Both clients enter or can open the active game.
- The current player sees `Your turn`; the other player sees a waiting state.
- The turn countdown is visible and moves toward expiry.
- Multi-word input is blocked on the client.
- The current player submits one valid word.
- Both clients see the story timeline update.
- Use `Random word` on a current turn and verify the suggestion can populate the input.
- Let a turn expire and verify `Skip expired` advances the game.
- Continue until the game reaches voting.
- Each player votes in all categories.
- Results update after votes and remain visible after the game finishes.
- Use `Share story`; verify the native share sheet opens with the final story text.

## Realtime And Lifecycle

- While both clients are in the lobby, have Player B leave; Player A sees the participant state update after refresh or realtime delivery.
- Rejoin Player B, then have Player A kick Player B; Player B is removed from the room and sees the removal message.
- Rejoin Player B, then have Player A close the room; Player B is returned from the active room state and sees the room-closed message.
- Start a fresh room, background one client during gameplay, submit a word from the other client, then foreground the backgrounded client; it refreshes to the current room/game state.
- Background a client in the lobby, start the game from the other client, then foreground it; it can resume/open the active game.

## Session Handling

- Close and reopen the app after sign-in; the saved session restores and refreshes tokens.
- Sign out; the app returns to auth state and subsequent app reopen does not restore the old session.
- If backend is temporarily unavailable during restore, the app warns that refresh failed instead of losing local session state.

## Pass Criteria

Phase 9 can be considered manually accepted when:

- Two users can complete one full game from auth through final results.
- Realtime or foreground refresh keeps both clients consistent during lobby and gameplay.
- Room close, kick, leave, and game-start lifecycle states are understandable on mobile.
- No raw backend stack traces or provider errors appear in the mobile UI.
- `npm run test` and `npm run typecheck` pass in `mobile`.
