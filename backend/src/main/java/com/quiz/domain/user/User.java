package com.quiz.domain.user;

import com.quiz.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_oauth", columnList = "oauth_provider, oauth_id")
    }
)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    // OAuth 로그인일 경우 null 가능
    @Column(length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false, length = 20)
    private OAuthProvider oauthProvider;

    @Column(name = "oauth_id", length = 100)
    private String oauthId;

    @Builder
    private User(String email, String nickname, String passwordHash, UserRole role,
                 OAuthProvider oauthProvider, String oauthId) {
        this.email = email;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.role = role;
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 기존 LOCAL 계정에 OAuth 정보를 연결 (계정 병합).
     */
    public void linkOAuth(OAuthProvider provider, String oauthId) {
        this.oauthProvider = provider;
        this.oauthId = oauthId;
    }
}
