package com.chainreaction.vote;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.game.domain.Game;
import com.chainreaction.game.domain.GameStatus;
import com.chainreaction.game.domain.StorySegment;
import com.chainreaction.game.repository.GameRepository;
import com.chainreaction.game.repository.StorySegmentRepository;
import com.chainreaction.observability.ApplicationMetrics;
import com.chainreaction.observability.ApplicationObservations;
import com.chainreaction.realtime.api.RealtimeEventType;
import com.chainreaction.realtime.service.RealtimeEventPublisher;
import com.chainreaction.room.domain.RoomParticipant;
import com.chainreaction.room.domain.RoomParticipantStatus;
import com.chainreaction.room.repository.RoomParticipantRepository;
import com.chainreaction.vote.api.SubmitVoteRequest;
import com.chainreaction.vote.api.VoteCategoryResultResponse;
import com.chainreaction.vote.api.VoteResponse;
import com.chainreaction.vote.api.VoteResultsResponse;
import com.chainreaction.vote.api.VoteTargetResultResponse;

@Service
public class VoteService {

    private final VoteRepository voteRepository;
    private final VoteResultRepository voteResultRepository;
    private final GameRepository gameRepository;
    private final StorySegmentRepository storySegmentRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final ApplicationMetrics applicationMetrics;
    private final ApplicationObservations applicationObservations;

    public VoteService(
            VoteRepository voteRepository,
            VoteResultRepository voteResultRepository,
            GameRepository gameRepository,
            StorySegmentRepository storySegmentRepository,
            RoomParticipantRepository roomParticipantRepository,
            RealtimeEventPublisher realtimeEventPublisher,
            ApplicationMetrics applicationMetrics,
            ApplicationObservations applicationObservations) {
        this.voteRepository = voteRepository;
        this.voteResultRepository = voteResultRepository;
        this.gameRepository = gameRepository;
        this.storySegmentRepository = storySegmentRepository;
        this.roomParticipantRepository = roomParticipantRepository;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.applicationMetrics = applicationMetrics;
        this.applicationObservations = applicationObservations;
    }

    @Transactional
    public VoteResponse submitVote(UUID userId, UUID gameId, SubmitVoteRequest request) {
        return applicationObservations.observe(
                "vote.submit",
                () -> submitVoteObserved(userId, gameId, request));
    }

    private VoteResponse submitVoteObserved(UUID userId, UUID gameId, SubmitVoteRequest request) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Game does not exist."));
        requireVotingOpen(game);
        requireActiveParticipant(game.getRoom().getId(), userId);
        if (voteRepository.existsByGameIdAndVoterUserIdAndCategory(gameId, userId, request.category())) {
            throw new ApiException(ErrorCode.DUPLICATE_VOTE, HttpStatus.CONFLICT,
                    "Player has already voted in this category.");
        }

        UUID targetUserId = null;
        UUID targetStorySegmentId = null;
        if (request.category().targetsPlayer()) {
            targetUserId = requirePlayerTarget(game, request);
        } else {
            targetStorySegmentId = requireStorySegmentTarget(game, request);
        }

        Vote vote = voteRepository.save(new Vote(gameId, userId, request.category(), targetUserId, targetStorySegmentId));
        VoteResultsResponse results = calculateAndPersistResults(game);
        realtimeEventPublisher.publishGameEvent(
                game.getRoom().getId(),
                game.getId(),
                RealtimeEventType.VOTE_RESULTS_UPDATED,
                results);
        finishGameIfVotingComplete(game, results);
        return VoteResponse.from(vote);
    }

    @Transactional(readOnly = true)
    public VoteResultsResponse results(UUID userId, UUID gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Game does not exist."));
        requireResultsVisible(game);
        requireActiveParticipant(game.getRoom().getId(), userId);

        return persistedResults(gameId);
    }

    private VoteResultsResponse calculateAndPersistResults(Game game) {
        VoteResultsResponse results = calculatedResults(game.getId());
        voteResultRepository.deleteAllByGameId(game.getId());
        voteResultRepository.saveAll(results.categories()
                .stream()
                .flatMap(category -> {
                    List<VoteTargetResultResponse> rankedResults = category.results();
                    return java.util.stream.IntStream.range(0, rankedResults.size())
                            .mapToObj(index -> new VoteResult(
                                    game.getId(),
                                    category.category(),
                                    index + 1,
                                    rankedResults.get(index).targetUserId(),
                                    rankedResults.get(index).targetStorySegmentId(),
                                    rankedResults.get(index).voteCount()));
                })
                .toList());
        return results;
    }

    private VoteResultsResponse calculatedResults(UUID gameId) {
        Map<VoteCategory, List<Vote>> votesByCategory = voteRepository.findAllByGameIdOrderByCreatedAtAsc(gameId)
                .stream()
                .collect(Collectors.groupingBy(Vote::getCategory, () -> new EnumMap<>(VoteCategory.class), Collectors.toList()));
        List<VoteCategoryResultResponse> categories = List.of(VoteCategory.values())
                .stream()
                .map(category -> new VoteCategoryResultResponse(
                        category,
                        rankedTargets(category, votesByCategory.getOrDefault(category, List.of()))))
                .toList();
        return new VoteResultsResponse(gameId, categories);
    }

    private VoteResultsResponse persistedResults(UUID gameId) {
        Map<VoteCategory, List<VoteResult>> resultsByCategory = voteResultRepository
                .findAllByGameIdOrderByCategoryAscResultRankAsc(gameId)
                .stream()
                .collect(Collectors.groupingBy(
                        VoteResult::getCategory,
                        () -> new EnumMap<>(VoteCategory.class),
                        Collectors.toList()));
        List<VoteCategoryResultResponse> categories = List.of(VoteCategory.values())
                .stream()
                .map(category -> new VoteCategoryResultResponse(
                        category,
                        resultsByCategory.getOrDefault(category, List.of())
                                .stream()
                                .map(result -> new VoteTargetResultResponse(
                                        result.getTargetUserId(),
                                        result.getTargetStorySegmentId(),
                                        result.getVoteCount()))
                                .toList()))
                .toList();
        return new VoteResultsResponse(gameId, categories);
    }

    private List<VoteTargetResultResponse> rankedTargets(VoteCategory category, List<Vote> votes) {
        return votes.stream()
                .collect(Collectors.groupingBy(
                        vote -> category.targetsPlayer() ? vote.getTargetUserId() : vote.getTargetStorySegmentId(),
                        Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().toString()))
                .map(entry -> category.targetsPlayer()
                        ? new VoteTargetResultResponse(entry.getKey(), null, entry.getValue())
                        : new VoteTargetResultResponse(null, entry.getKey(), entry.getValue()))
                .toList();
    }

    private void requireVotingOpen(Game game) {
        if (game.getStatus() != GameStatus.VOTING) {
            throw new ApiException(ErrorCode.VOTING_NOT_OPEN, HttpStatus.CONFLICT,
                    "Voting is not open for this game.");
        }
    }

    private void requireResultsVisible(Game game) {
        if (game.getStatus() != GameStatus.VOTING && game.getStatus() != GameStatus.FINISHED) {
            throw new ApiException(ErrorCode.VOTING_NOT_OPEN, HttpStatus.CONFLICT,
                    "Voting results are not available for this game.");
        }
    }

    private void finishGameIfVotingComplete(Game game, VoteResultsResponse results) {
        int activeParticipantCount = activeParticipantCount(game);
        long requiredVotes = (long) activeParticipantCount * VoteCategory.values().length;
        if (requiredVotes == 0 || voteRepository.countByGameId(game.getId()) < requiredVotes) {
            return;
        }

        game.finish();
        applicationMetrics.recordGameFinished();
        realtimeEventPublisher.publishGameEvent(
                game.getRoom().getId(),
                game.getId(),
                RealtimeEventType.GAME_FINISHED,
                results);
    }

    private void requireActiveParticipant(UUID roomId, UUID userId) {
        RoomParticipant participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                        "User is not a room participant."));
        if (participant.getStatus() != RoomParticipantStatus.JOINED) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                    "User is not an active room participant.");
        }
    }

    private int activeParticipantCount(Game game) {
        return (int) roomParticipantRepository.findAllByRoomIdOrderByJoinedAtAsc(game.getRoom().getId())
                .stream()
                .filter(participant -> participant.getStatus() == RoomParticipantStatus.JOINED)
                .count();
    }

    private UUID requirePlayerTarget(Game game, SubmitVoteRequest request) {
        if (request.targetUserId() == null || request.targetStorySegmentId() != null) {
            throw new ApiException(ErrorCode.INVALID_VOTE_TARGET, HttpStatus.BAD_REQUEST,
                    "Vote category requires one player target.");
        }
        requireActiveParticipant(game.getRoom().getId(), request.targetUserId());
        return request.targetUserId();
    }

    private UUID requireStorySegmentTarget(Game game, SubmitVoteRequest request) {
        if (request.targetStorySegmentId() == null || request.targetUserId() != null) {
            throw new ApiException(ErrorCode.INVALID_VOTE_TARGET, HttpStatus.BAD_REQUEST,
                    "Vote category requires one story segment target.");
        }
        StorySegment segment = storySegmentRepository.findByIdAndStoryGameId(request.targetStorySegmentId(), game.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_VOTE_TARGET, HttpStatus.BAD_REQUEST,
                        "Vote target story segment does not belong to this game."));
        if (segment.getAuthorUserId() == null) {
            throw new ApiException(ErrorCode.INVALID_VOTE_TARGET, HttpStatus.BAD_REQUEST,
                    "Opening story segment cannot receive this vote.");
        }
        return request.targetStorySegmentId();
    }
}
