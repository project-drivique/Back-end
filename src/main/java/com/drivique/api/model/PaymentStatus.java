package com.drivique.api.model;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="payment_statuses",schema="billing")
public class PaymentStatus { @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(nullable=false,unique=true,length=30) private String code; @Column(nullable=false,length=100) private String name; @Column(name="is_final",nullable=false) private boolean finalStatus; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now(); @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now(); protected PaymentStatus(){} public UUID getId(){return id;} public String getCode(){return code;} }
