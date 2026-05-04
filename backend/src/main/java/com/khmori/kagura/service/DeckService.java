package com.khmori.kagura.service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.khmori.kagura.dto.GraphEdge;
import com.khmori.kagura.dto.GraphNode;
import com.khmori.kagura.dto.GraphResponse;
import com.khmori.kagura.entity.Compound;
import com.khmori.kagura.entity.Kanji;
import com.khmori.kagura.repository.CompoundRepository;
import com.khmori.kagura.repository.KanjiRepository;

@Service
public class DeckService {

    public static final double DEFAULT_THRESHOLD = 0.3;

    private final AnkiDeckParser parser;
    private final KanjiRepository kanjiRepository;
    private final CompoundRepository compoundRepository;

    public DeckService(AnkiDeckParser parser,
                       KanjiRepository kanjiRepository,
                       CompoundRepository compoundRepository) {
        this.parser = parser;
        this.kanjiRepository = kanjiRepository;
        this.compoundRepository = compoundRepository;
    }

    public GraphResponse buildGraph(MultipartFile apkg, double threshold) throws IOException, SQLException {
        Map<String, Double> scores = parser.extractKanjiScores(apkg);
        Set<String> known = scores.entrySet().stream()
            .filter(e -> e.getValue() >= threshold)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        if (known.isEmpty()) return emptyGraph();

        List<Integer> ids = compoundRepository.findIdsAllKanjiKnown2Char(known);
        if (ids.isEmpty()) return emptyGraph();

        List<Compound> compounds = compoundRepository.findAllWithKanjiByIdIn(ids);
        List<Kanji> kanji = kanjiRepository.findByKanjiIn(known);

        GraphResponse res = new GraphResponse();
        res.setNodes(kanji.stream().map(k -> toNode(k, scores)).toList());
        res.setEdges(compounds.stream().map(DeckService::toEdge).toList());
        return res;
    }

    private static GraphNode toNode(Kanji k, Map<String, Double> scores) {
        GraphNode n = new GraphNode();
        n.setId(k.getKanji());
        n.setLabel(k.getKanji());
        n.setKnown(true);
        n.setScore(scores.get(k.getKanji()));
        n.setMeaning(arrayToList(k.getMeaning()));
        n.setJlptLevel(k.getJlptLevel());
        n.setStrokeCount(k.getStrokeCount());
        n.setFrequency(k.getFrequency());
        return n;
    }

    private static GraphEdge toEdge(Compound c) {
        String text = c.getCompound();
        GraphEdge e = new GraphEdge();
        e.setId(String.valueOf(c.getId()));
        e.setSource(String.valueOf(text.charAt(0)));
        e.setTarget(String.valueOf(text.charAt(1)));
        e.setCompound(text);
        e.setReading(arrayToList(c.getReading()));
        e.setMeaning(arrayToList(c.getMeaning()));
        e.setCommon(c.getCommon());
        return e;
    }

    private static List<String> arrayToList(String[] arr) {
        return arr == null ? List.of() : new ArrayList<>(Arrays.asList(arr));
    }

    private static GraphResponse emptyGraph() {
        GraphResponse res = new GraphResponse();
        res.setNodes(List.of());
        res.setEdges(List.of());
        return res;
    }
}
