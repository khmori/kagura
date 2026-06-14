package com.khmori.kagura.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khmori.kagura.dto.UserConfig;
import com.khmori.kagura.entity.User;
import com.khmori.kagura.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepo;

    public UserConfig getConfigForCurrentUser() {
        User user = userRepo.findByProviderAndProviderUserId("manual", "test-1").orElseThrow();
        UserConfig config = new UserConfig();
        config.selectedDeck = user.getSelectedDeck();
        config.fieldMapping = user.getFieldMapping();
        config.studyMode = user.getStudyMode();
        return config;
    }

    @Transactional
    public void setConfigForCurrentUser(UserConfig config) {
        User user = userRepo.findByProviderAndProviderUserId("manual", "test-1").orElseThrow();
        user.setSelectedDeck(config.selectedDeck);
        if (config.fieldMapping != null) {
            user.setFieldMapping(config.fieldMapping);
        }
        if (config.studyMode != null) {
            user.setStudyMode(config.studyMode);
        }
        userRepo.save(user);
    }
}
