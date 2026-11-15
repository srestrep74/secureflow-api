package com.secureflow.identity.application;

import com.secureflow.identity.domain.User;

public interface LoginTokenPort {
    String generateRefreshToken();

    String digestRefreshToken(String token);

    String issueAccessToken(User user, SessionFamily family);
}
