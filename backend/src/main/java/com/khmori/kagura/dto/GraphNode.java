package com.khmori.kagura.dto;

import java.util.List;

import lombok.Data;

@Data
public class GraphNode {
    private String id;
    private String label;
    private Boolean known;
    private Double score;
    private List<String> meaning;
    private Integer jlptLevel;
    private Integer strokeCount;
    private Integer frequency;
}
