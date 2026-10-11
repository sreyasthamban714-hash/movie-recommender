package com.movie.service;

import com.movie.model.Movie;
import com.movie.model.Rating;
import com.movie.model.ScoredMovie;
import com.movie.repository.MovieRepository;
import com.movie.repository.RatingRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final MovieRepository movieRepo;
    private final RatingRepository ratingRepo;

    private boolean loaded = false;
    private final Map<Long, Movie> movies = new HashMap<>();
    private final Map<Integer, Map<Long, Double>> userRatings = new HashMap<>();
    private final Map<Long, Map<Integer, Double>> movieRatings = new HashMap<>();
    private final Map<Long, Double> norms = new HashMap<>();

    public RecommendationService(MovieRepository movieRepo, RatingRepository ratingRepo) {
        this.movieRepo = movieRepo;
        this.ratingRepo = ratingRepo;
    }

    // Loads everything into memory once, on first use
    private synchronized void init() {
        if (loaded) return;
        movies.clear();
        userRatings.clear();
        movieRatings.clear();
        norms.clear();

        for (Movie m : movieRepo.findAll()) {
            movies.put(m.getId(), m);
        }
        for (Rating r : ratingRepo.findAll()) {
            userRatings.computeIfAbsent(r.getUserId(), k -> new HashMap<>())
                    .put(r.getMovieId(), r.getRating());
            movieRatings.computeIfAbsent(r.getMovieId(), k -> new HashMap<>())
                    .put(r.getUserId(), r.getRating());
        }

        if (movies.isEmpty() || movieRatings.isEmpty()) {
            return;
        }

        for (Map.Entry<Long, Map<Integer, Double>> e : movieRatings.entrySet()) {
            double sum = 0;
            for (double v : e.getValue().values()) sum += v * v;
            norms.put(e.getKey(), Math.sqrt(sum));
        }
        loaded = true;
    }

    private double avg(Map<Integer, Double> m) {
        return m.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public Movie getMovie(long id) {
        init();
        return movies.get(id);
    }

    public double getAverage(long id) {
        init();
        return avg(movieRatings.getOrDefault(id, Collections.emptyMap()));
    }

    public boolean hasUser(int userId) {
        init();
        return userRatings.containsKey(userId);
    }

    // Highest average rating, only movies with at least 50 ratings
    public List<ScoredMovie> topRated(int n) {
        init();
        return movieRatings.entrySet().stream()
                .filter(e -> e.getValue().size() >= 50 && movies.containsKey(e.getKey()))
                .map(e -> new ScoredMovie(movies.get(e.getKey()), avg(e.getValue())))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(n)
                .collect(Collectors.toList());
    }

    public List<ScoredMovie> search(String q) {
        init();
        String key = q.toLowerCase();
        return movies.values().stream()
                .filter(m -> m.getTitle().toLowerCase().contains(key))
                .map(m -> new ScoredMovie(m,
                        avg(movieRatings.getOrDefault(m.getId(), Collections.emptyMap()))))
                .limit(30)
                .collect(Collectors.toList());
    }

    // Cosine similarity between two movies, based on the users who rated them
    private double itemSimilarity(long a, long b) {
        Map<Integer, Double> ra = movieRatings.get(a);
        Map<Integer, Double> rb = movieRatings.get(b);
        if (ra == null || rb == null) return 0;
        if (ra.size() > rb.size()) {
            Map<Integer, Double> t = ra;
            ra = rb;
            rb = t;
        }
        double dot = 0;
        for (Map.Entry<Integer, Double> e : ra.entrySet()) {
            Double v = rb.get(e.getKey());
            if (v != null) dot += e.getValue() * v;
        }
        if (dot == 0) return 0;
        return dot / (norms.get(a) * norms.get(b));
    }

    private Set<String> genreSet(Movie m) {
        return new HashSet<>(Arrays.asList(m.getGenres().split("\\|")));
    }

    private double jaccard(Set<String> a, Set<String> b) {
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }

    // Recommendations for one user (item-based collaborative filtering)
    public List<ScoredMovie> recommend(int userId, int n) {
        init();
        Map<Long, Double> mine = userRatings.get(userId);
        if (mine == null) return topRated(n);

        // the user's 30 highest-rated movies
        List<Map.Entry<Long, Double>> liked = mine.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(30)
                .collect(Collectors.toList());

        List<ScoredMovie> result = new ArrayList<>();
        for (Long candidate : movieRatings.keySet()) {
            if (mine.containsKey(candidate)) continue;
            if (movieRatings.get(candidate).size() < 5) continue;
            if (!movies.containsKey(candidate)) continue;
            double score = 0;
            for (Map.Entry<Long, Double> l : liked) {
                score += itemSimilarity(candidate, l.getKey()) * l.getValue();
            }
            if (score > 0) result.add(new ScoredMovie(movies.get(candidate), score));
        }
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (result.isEmpty()) return topRated(n);

        // turn scores into percentages (best match = 100)
        double max = result.get(0).getScore();
        return result.stream()
                .limit(n)
                .map(s -> new ScoredMovie(s.getMovie(), s.getScore() / max * 100))
                .collect(Collectors.toList());
    }

    // Movies similar to a given movie: 70% rating similarity + 30% genre similarity
    public List<ScoredMovie> similarTo(long movieId, int n) {
        init();
        Movie target = movies.get(movieId);
        if (target == null) return new ArrayList<>();
        Set<String> genres = genreSet(target);

        List<ScoredMovie> result = new ArrayList<>();
        for (Long c : movieRatings.keySet()) {
            if (c == movieId) continue;
            if (movieRatings.get(c).size() < 5) continue;
            if (!movies.containsKey(c)) continue;
            double cf = itemSimilarity(movieId, c);
            double content = jaccard(genres, genreSet(movies.get(c)));
            double score = (0.7 * cf + 0.3 * content) * 100;
            result.add(new ScoredMovie(movies.get(c), score));
        }
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return result.stream().limit(n).collect(Collectors.toList());
    }
}