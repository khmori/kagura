package com.khmori.kagura.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.khmori.kagura.dto.SyncRequest;
import com.khmori.kagura.dto.SyncResponse;
import com.khmori.kagura.service.SyncService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SyncController {
    private final SyncService syncService;

    @PostMapping("/sync")
    public SyncResponse sync(@RequestBody SyncRequest req) {
        return syncService.sync(req);
    }
}
