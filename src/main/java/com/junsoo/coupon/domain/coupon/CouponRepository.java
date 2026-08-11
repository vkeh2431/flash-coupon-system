package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Query("select c from Coupon c join fetch c.campaign where c.user.id = :userId and c.status = :status and c.expiresAt > now() ORDER BY c.expiresAt asc")
    List<Coupon> findAvailableByUserId(@Param("userId") Long userId, @Param("status") Status status);

    Optional<Coupon> findByIdAndUserId(Long id, Long userId);
}
