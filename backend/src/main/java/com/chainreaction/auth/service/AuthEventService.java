package com.chainreaction.auth.service;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.chainreaction.auth.domain.AuthEvent;
import com.chainreaction.auth.repository.AuthEventRepository;
import com.chainreaction.user.domain.User;

@Service
public class AuthEventService {

    private final AuthEventRepository authEventRepository;

    public AuthEventService(AuthEventRepository authEventRepository) {
        this.authEventRepository = authEventRepository;
    }

    public void record(User user, String email, String eventType, String outcome, String reason) {
        authEventRepository.save(new AuthEvent(
                user,
                email,
                eventType,
                outcome,
                reason,
                MDC.get("correlationId")));
    }
}
