package com.drivique.api.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="promotions",schema="catalog") public class Promotion {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,unique=true,length=50) private String code;
 @Column(name="offer_type",nullable=false,length=20) private String offerType;
 @Column(name="discount_type",nullable=false,length=20) private String discountType;
 @Column(name="discount_value",nullable=false,precision=12,scale=2) private BigDecimal discountValue;
 @Column(name="starts_at",nullable=false) private Instant startsAt; @Column(name="ends_at",nullable=false) private Instant endsAt;
 @Column(name="minimum_rental_days",nullable=false) private int minimumRentalDays=1; @Column(name="max_uses_limit") private Integer maxUsesLimit;
 @Column(name="current_uses_count",nullable=false) private int currentUsesCount; @Column(name="is_active",nullable=false) private boolean active=true;
 protected Promotion(){} public Promotion(String code,String offerType,String discountType,BigDecimal discountValue,Instant startsAt,Instant endsAt,int minimumRentalDays,Integer maxUsesLimit,int currentUsesCount){this.code=code;this.offerType=offerType;this.discountType=discountType;this.discountValue=discountValue;this.startsAt=startsAt;this.endsAt=endsAt;this.minimumRentalDays=minimumRentalDays;this.maxUsesLimit=maxUsesLimit;this.currentUsesCount=currentUsesCount;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getOfferType(){return offerType;} public String getDiscountType(){return discountType;} public BigDecimal getDiscountValue(){return discountValue;} public Instant getStartsAt(){return startsAt;} public Instant getEndsAt(){return endsAt;} public int getMinimumRentalDays(){return minimumRentalDays;} public Integer getMaxUsesLimit(){return maxUsesLimit;} public int getCurrentUsesCount(){return currentUsesCount;} public void setCurrentUsesCount(int currentUsesCount){this.currentUsesCount=currentUsesCount;} public void incrementUsesCount(){this.currentUsesCount++;} public boolean isActive(){return active;}
}

