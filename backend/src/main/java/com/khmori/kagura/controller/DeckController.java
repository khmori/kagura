package com.khmori.kagura.controller;

import java.io.IOException;
import java.sql.SQLException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.khmori.kagura.dto.GraphResponse;
import com.khmori.kagura.service.DeckService;

@RestController
@RequestMapping("/api")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @PostMapping(value = "/decks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GraphResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "threshold", required = false) Double threshold)
            throws IOException, SQLException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".apkg")) {
            return ResponseEntity.badRequest().build();
        }
        double t = threshold != null ? threshold : DeckService.DEFAULT_THRESHOLD;
        if (t < 0.0 || t > 1.0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(deckService.buildGraph(file, t));
    }
}
