package com.drivique.api.dto;

public record OAuthUserInfo(
        String provider,
        String providerUserId,
        String email,
        String firstName,
        String lastName,
        String nonce
) {}
