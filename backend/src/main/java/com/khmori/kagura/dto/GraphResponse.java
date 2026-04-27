package com.khmori.kagura.dto;

import java.util.List;

import lombok.Data;

@Data
public class GraphResponse {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
}
