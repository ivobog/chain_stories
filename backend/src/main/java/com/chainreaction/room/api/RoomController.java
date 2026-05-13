package com.chainreaction.room.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chainreaction.common.security.CurrentUserPrincipal;
import com.chainreaction.room.service.RoomService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public RoomResponse createRoom(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody CreateRoomRequest request) {
        return roomService.createRoom(principal.getUserId(), request);
    }

    @GetMapping
    public List<RoomSummaryResponse> listMyRooms(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return roomService.listMyRooms(principal.getUserId());
    }

    @GetMapping("/code/{roomCode}/preview")
    public RoomPreviewResponse previewRoom(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable String roomCode) {
        return roomService.previewRoom(principal.getUserId(), roomCode);
    }

    @PostMapping("/{roomCode}/join")
    public RoomResponse joinRoom(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable String roomCode) {
        return roomService.joinRoom(principal.getUserId(), roomCode);
    }

    @GetMapping("/{roomId}")
    public RoomResponse getRoom(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID roomId) {
        return roomService.getRoom(principal.getUserId(), roomId);
    }

    @PostMapping("/{roomId}/close")
    public RoomResponse closeRoom(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID roomId) {
        return roomService.closeRoom(principal.getUserId(), roomId);
    }

    @PatchMapping("/{roomId}/settings")
    public RoomResponse updateSettings(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateRoomSettingsRequest request) {
        return roomService.updateSettings(principal.getUserId(), roomId, request);
    }

    @PostMapping("/{roomId}/leave")
    public RoomResponse leaveRoom(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID roomId) {
        return roomService.leaveRoom(principal.getUserId(), roomId);
    }

    @PostMapping("/{roomId}/participants/{userId}/kick")
    public RoomResponse kickParticipant(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID userId) {
        return roomService.kickParticipant(principal.getUserId(), roomId, userId);
    }
}
