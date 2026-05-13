package com.chainreaction.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.chainreaction.user.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    @Query("""
            select count(user) > 0
            from User user
            where lower(user.email) = lower(:email)
              and user.status <> com.chainreaction.user.domain.UserStatus.DELETED
            """)
    boolean existsNonDeletedByEmailIgnoreCase(String email);
}
