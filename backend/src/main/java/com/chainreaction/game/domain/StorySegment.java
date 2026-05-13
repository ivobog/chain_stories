package com.chainreaction.game.domain;

import java.time.Instant;
import java.util.UUID;

import com.chainreaction.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "story_segments")
public class StorySegment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id")
    private GameTurn turn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private User author;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StorySegment() {
    }

    public StorySegment(Story story, GameTurn turn, User author, int sequenceNumber, String content) {
        this.id = UUID.randomUUID();
        this.story = story;
        this.turn = turn;
        this.author = author;
        this.sequenceNumber = sequenceNumber;
        this.content = content;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Integer getTurnNumber() {
        return turn == null ? null : turn.getTurnNumber();
    }

    public UUID getAuthorUserId() {
        return author == null ? null : author.getId();
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getContent() {
        return content;
    }
}
