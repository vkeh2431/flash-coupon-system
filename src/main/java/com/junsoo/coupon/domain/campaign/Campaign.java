package com.junsoo.coupon.domain.campaign;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDateTime opensAt;

    @Column(nullable = false)
    private LocalDateTime closesAt;

    private int totalQuantity;

    private int remainingQuantity;

    private boolean paused;

    public Campaign(
            String name,
            LocalDateTime opensAt,
            LocalDateTime closesAt,
            int totalQuantity
            ) {
        if(opensAt.isAfter(closesAt)) {
            throw new IllegalArgumentException("opensAt은 closesAt보다 빠른 날짜여야 합니다.");
        }
        if(totalQuantity <= 0) {
            throw new IllegalArgumentException("총 수량은 0보다 커야 합니다.");
        }


        this.name = name;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.paused = false;
    }

    public void pause() {
        this.paused = true;
    }

    public void resume() {
        this.paused = false;
    }
}
