package com.movie.controller;

import com.movie.model.Movie;
import com.movie.model.Rating;
import com.movie.repository.MovieRepository;
import com.movie.repository.RatingRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class AnalysisController {

    private final MovieRepository movieRepo;
    private final RatingRepository ratingRepo;

    public AnalysisController(MovieRepository movieRepo, RatingRepository ratingRepo) {
        this.movieRepo = movieRepo;
        this.ratingRepo = ratingRepo;
    }

    @GetMapping("/analysis")
    public String analysis(Model model) {
        List<Movie> movies = movieRepo.findAll();
        List<Rating> ratings = ratingRepo.findAll();

        Map<Long, String> titles = new HashMap<>();
        for (Movie m : movies) titles.put(m.getId(), m.getTitle());

        // ---- rating distribution + basic statistics ----
        TreeMap<Double, Integer> dist = new TreeMap<>();
        Set<Integer> users = new HashSet<>();
        Map<Long, Integer> movieCounts = new HashMap<>();
        double sum = 0;
        for (Rating r : ratings) {
            dist.merge(r.getRating(), 1, Integer::sum);
            users.add(r.getUserId());
            movieCounts.merge(r.getMovieId(), 1, Integer::sum);
            sum += r.getRating();
        }
        List<String> ratingLabels = new ArrayList<>();
        List<Integer> ratingValues = new ArrayList<>();
        for (Map.Entry<Double, Integer> e : dist.entrySet()) {
            ratingLabels.add(String.valueOf(e.getKey()));
            ratingValues.add(e.getValue());
        }

        // ---- top 10 genres by number of movies ----
        Map<String, Integer> genreCounts = new HashMap<>();
        for (Movie m : movies) {
            for (String g : m.getGenres().split("\\|")) {
                genreCounts.merge(g, 1, Integer::sum);
            }
        }
        genreCounts.remove("(no genres listed)");
        List<Map.Entry<String, Integer>> topGenres = genreCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(10)
                .collect(Collectors.toList());
        List<String> genreLabels = new ArrayList<>();
        List<Integer> genreValues = new ArrayList<>();
        for (Map.Entry<String, Integer> e : topGenres) {
            genreLabels.add(e.getKey());
            genreValues.add(e.getValue());
        }

        // ---- top 10 most rated movies ----
        List<Map.Entry<Long, Integer>> topMovies = movieCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(10)
                .collect(Collectors.toList());
        List<String> movieLabels = new ArrayList<>();
        List<Integer> movieValues = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : topMovies) {
            movieLabels.add(titles.getOrDefault(e.getKey(), "Unknown"));
            movieValues.add(e.getValue());
        }

        model.addAttribute("numUsers", users.size());
        model.addAttribute("numMovies", movies.size());
        model.addAttribute("numRatings", ratings.size());
        model.addAttribute("avgRating", sum / ratings.size());
        model.addAttribute("sparsity",
                (1.0 - (double) ratings.size() / ((double) users.size() * movies.size())) * 100);

        model.addAttribute("ratingLabels", ratingLabels);
        model.addAttribute("ratingValues", ratingValues);
        model.addAttribute("genreLabels", genreLabels);
        model.addAttribute("genreValues", genreValues);
        model.addAttribute("movieLabels", movieLabels);
        model.addAttribute("movieValues", movieValues);
        return "analysis";
    }
}