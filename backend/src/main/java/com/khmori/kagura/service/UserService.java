package com.khmori.kagura.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.khmori.kagura.entity.User;
import com.khmori.kagura.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepo;

    public Map<String, Map<String, String>> getFieldMappingForCurrentUser() {
        User user = userRepo.findByProviderAndProviderUserId("manual", "test-1").orElseThrow();
        return user.getFieldMapping();
    }

    public void setFieldMappingForCurrentUser(Map<String, Map<String, String>> mapping) {
        User user = userRepo.findByProviderAndProviderUserId("manual", "test-1").orElseThrow();
        user.setFieldMapping(mapping);
    }
}
