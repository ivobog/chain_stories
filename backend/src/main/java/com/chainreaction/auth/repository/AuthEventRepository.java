package com.chainreaction.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chainreaction.auth.domain.AuthEvent;

public interface AuthEventRepository extends JpaRepository<AuthEvent, UUID> {
}
