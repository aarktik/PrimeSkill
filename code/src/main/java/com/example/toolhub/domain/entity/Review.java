package com.example.toolhub.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "reviews",
        indexes = @Index(name = "idx_reviews_tool_id", columnList = "tool_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reviews_user_tool",
                columnNames = {"user_id", "tool_id"}))
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_id", nullable = false)
    private Tool tool;

    @Column(nullable = false)
    private Short rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    protected Review() {
    }

    public Review(User user, Tool tool, Short rating, String comment) {
        this.user = user;
        this.tool = tool;
        this.rating = rating;
        this.comment = comment;
    }

    public void update(Short rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Tool getTool() {
        return tool;
    }

    public Short getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }
}
