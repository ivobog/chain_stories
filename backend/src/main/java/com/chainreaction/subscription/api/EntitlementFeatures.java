package com.chainreaction.subscription.api;

public record EntitlementFeatures(
        int maxPlayersPerRoom,
        boolean canShareStories,
        boolean canGenerateManga,
        boolean canGenerateVideo,
        boolean canCreateCreatorRooms) {
}
