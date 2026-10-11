package com.movie.loader;

import com.movie.model.Movie;
import com.movie.model.Rating;
import com.movie.repository.MovieRepository;
import com.movie.repository.RatingRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private final MovieRepository movieRepo;
    private final RatingRepository ratingRepo;

    public DataLoader(MovieRepository movieRepo, RatingRepository ratingRepo) {
        this.movieRepo = movieRepo;
        this.ratingRepo = ratingRepo;
    }

    @Override
    public void run(String... args) throws Exception {
        loadMovies();
        loadRatings();
        System.out.println("Loaded movies: " + movieRepo.count()
                + ", ratings: " + ratingRepo.count());
    }

    private void loadMovies() throws Exception {
        List<Movie> batch = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/movies.csv").getInputStream()))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                // split on commas that are not inside quotes
                String[] p = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                Movie m = new Movie();
                m.setId(Long.parseLong(p[0]));
                m.setTitle(p[1].replace("\"", ""));
                m.setGenres(p[2]);
                batch.add(m);
            }
        }
        movieRepo.saveAll(batch);
    }

    private void loadRatings() throws Exception {
        List<Rating> batch = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/ratings.csv").getInputStream()))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                Rating r = new Rating();
                r.setUserId(Integer.parseInt(p[0]));
                r.setMovieId(Long.parseLong(p[1]));
                r.setRating(Double.parseDouble(p[2]));
                batch.add(r);
            }
        }
        ratingRepo.saveAll(batch);
    }
}