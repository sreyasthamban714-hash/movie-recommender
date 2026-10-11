package com.movie.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScoredMovie {
    private Movie movie;
    private double score;
}