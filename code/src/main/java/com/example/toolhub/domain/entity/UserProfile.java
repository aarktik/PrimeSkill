package com.example.toolhub.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
public class UserProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    protected UserProfile() {
    }

    public UserProfile(String displayName, String bio, String avatarUrl) {
        this.displayName = displayName;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
    }

    public void assignUser(User user) {
        this.user = user;
    }

    public void update(String displayName, String bio, String avatarUrl) {
        this.displayName = displayName;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }
}