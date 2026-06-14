package com.khmori.kagura.dto;

import java.util.List;

public class KanjiDetailsDto {
    public String kanji;
    public String[] onReading;
    public String[] kunReading;
    public String[] meaning;
    public Integer grade;
    public Integer jlptLevel;
    public Double kankenLevel;
    public Integer strokeCount;
    public List<WordEntry> words;

    public static class WordEntry {
        public String word;
        public String[] reading;
        public String[] meaning;
        public boolean common;
        public Integer frequencyRank;
        public String retentionStatus;
    }
}
