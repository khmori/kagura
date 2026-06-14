package com.khmori.kagura.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.khmori.kagura.dto.UserConfig;
import com.khmori.kagura.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me/config")
    public UserConfig getConfig() {
        return userService.getConfigForCurrentUser();
    }

    @PutMapping("/me/config")
    public void putConfig(@RequestBody UserConfig config) {
        userService.setConfigForCurrentUser(config);
    }
}
