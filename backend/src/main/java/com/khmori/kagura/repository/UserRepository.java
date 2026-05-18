package com.khmori.kagura.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.khmori.kagura.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByProviderAndProviderUserId(String provider, String providerUserId);
}