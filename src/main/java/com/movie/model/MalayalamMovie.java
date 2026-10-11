package com.movie.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MalayalamMovie {
    private long id;
    private String title;
    private String year;
    private String overview;
    private String posterUrl;
    private double rating;
    private int votes;
    private List<String> genres;
    private double score;
}