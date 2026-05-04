package com.khmori.kagura.dto;

import java.util.List;

import lombok.Data;

@Data
public class GraphEdge {
    private String id;
    private String source;
    private String target;
    private String compound;
    private List<String> reading;
    private List<String> meaning;
    private Boolean common;
}