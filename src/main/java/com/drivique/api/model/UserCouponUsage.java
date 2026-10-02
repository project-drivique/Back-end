package com.drivique.api.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="user_coupon_usages",schema="catalog") public class UserCouponUsage {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="promotion_id", nullable=false) private Promotion promotion;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id", nullable=false) private User user;
 @Column(name="used_at", nullable=false) private Instant usedAt = Instant.now();
 @Column(name="discount_amount",nullable=false,precision=12,scale=2) private BigDecimal discountAmount;
 protected UserCouponUsage(){} public UserCouponUsage(Promotion promotion,User user,BigDecimal discountAmount){this.promotion=promotion;this.user=user;this.discountAmount=discountAmount;this.usedAt=Instant.now();}
 public UUID getId(){return id;} public Promotion getPromotion(){return promotion;} public User getUser(){return user;} public Instant getUsedAt(){return usedAt;} public BigDecimal getDiscountAmount(){return discountAmount;}
}

