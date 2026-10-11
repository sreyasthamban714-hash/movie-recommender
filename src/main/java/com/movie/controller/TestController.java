package com.movie.controller;

import com.movie.repository.MovieRepository;
import com.movie.repository.RatingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    private final MovieRepository movieRepo;
    private final RatingRepository ratingRepo;

    public TestController(MovieRepository movieRepo, RatingRepository ratingRepo) {
        this.movieRepo = movieRepo;
        this.ratingRepo = ratingRepo;
    }

    @GetMapping("/test")
    public String test() {
        return "Movies: " + movieRepo.count() + " | Ratings: " + ratingRepo.count();
    }
}