package com.khmori.kagura.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.khmori.kagura.dto.SyncRequest;
import com.khmori.kagura.dto.SyncResponse;
import com.khmori.kagura.dto.UserKanjiDto;
import com.khmori.kagura.service.SyncService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class SyncController {
    private final SyncService syncService;

    @PostMapping("/sync")
    public SyncResponse sync(@RequestBody SyncRequest req) {
        return syncService.sync(req);
    }

    @GetMapping("/user-kanji")
    public List<UserKanjiDto> getUserKanji() {
        return syncService.getUserKanji();
    }
}
