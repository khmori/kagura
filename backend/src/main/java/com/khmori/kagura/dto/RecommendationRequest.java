package com.khmori.kagura.dto;

import java.util.List;

import lombok.Data;

@Data
public class RecommendationRequest {
    private List<KanjiScore> kanjiScores;
    private Integer limit;
    private Double knownThreshold;
}
