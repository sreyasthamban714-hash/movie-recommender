package com.movie.controller;

import com.movie.model.Movie;
import com.movie.service.RecommendationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MovieController {

    private final RecommendationService service;

    public MovieController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("scoreLabel", "Avg rating");
        if (q != null && !q.isBlank()) {
            model.addAttribute("title", "Search results for \"" + q + "\"");
            model.addAttribute("movies", service.search(q));
        } else {
            model.addAttribute("title", "Top rated movies");
            model.addAttribute("movies", service.topRated(20));
        }
        return "index";
    }

    @GetMapping("/recommend")
    public String recommend(@RequestParam int userId, Model model) {
        if (service.hasUser(userId)) {
            model.addAttribute("title", "Recommended for user " + userId);
            model.addAttribute("scoreLabel", "Match %");
        } else {
            model.addAttribute("title", "User " + userId + " not found, showing top rated");
            model.addAttribute("scoreLabel", "Avg rating");
        }
        model.addAttribute("movies", service.recommend(userId, 12));
        return "index";
    }

    @GetMapping("/movie/{id}")
    public String detail(@PathVariable long id, Model model) {
        Movie movie = service.getMovie(id);
        if (movie == null) return "redirect:/";
        model.addAttribute("movie", movie);
        model.addAttribute("average", service.getAverage(id));
        model.addAttribute("similar", service.similarTo(id, 8));
        return "movie";
    }
}