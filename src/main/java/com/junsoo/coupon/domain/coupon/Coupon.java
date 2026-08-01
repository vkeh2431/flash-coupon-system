package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.domain.campaign.Campaign;
import com.junsoo.coupon.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_coupon", columnNames = {"user_id", "campaign_id"}),
        indexes = @Index(name = "index_campaign", columnList = "campaign_id")
)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime redeemedAt;

    public boolean isExpired() {
        return this.expiresAt.isBefore(LocalDateTime.now());
    }

    public void redeem() {
        this.redeemedAt = LocalDateTime.now();
        this.status = Status.REDEEMED;
    }

    public Coupon(User user, Campaign campaign) {
        this.user = user;
        this.campaign = campaign;
        this.status = Status.ISSUED;
        this.issuedAt = LocalDateTime.now();
        this.expiresAt = this.issuedAt.plusDays(7);
        this.redeemedAt = null;
    }
}
