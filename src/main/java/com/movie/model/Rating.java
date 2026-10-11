package com.movie.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Rating {
    @Id
    @GeneratedValue
    private Long id;
    private int userId;
    private long movieId;
    private double rating;
}