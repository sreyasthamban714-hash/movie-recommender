package com.movie.service;

import com.movie.model.MalayalamMovie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MalayalamService {

    @Value("${tmdb.api.key:}")
    private String apiKey;

    private final RestClient rest;
    private List<MalayalamMovie> cache = null;

    private static final Map<Integer, String> GENRES = Map.ofEntries(
            Map.entry(28, "Action"), Map.entry(12, "Adventure"), Map.entry(16, "Animation"),
            Map.entry(35, "Comedy"), Map.entry(80, "Crime"), Map.entry(99, "Documentary"),
            Map.entry(18, "Drama"), Map.entry(10751, "Family"), Map.entry(14, "Fantasy"),
            Map.entry(36, "History"), Map.entry(27, "Horror"), Map.entry(10402, "Music"),
            Map.entry(9648, "Mystery"), Map.entry(10749, "Romance"), Map.entry(878, "Sci-Fi"),
            Map.entry(10770, "TV Movie"), Map.entry(53, "Thriller"), Map.entry(10752, "War"),
            Map.entry(37, "Western"));

    public MalayalamService() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    // Loads about 100 popular Malayalam movies from TMDB (once, then cached)
    @SuppressWarnings("unchecked")
    public synchronized List<MalayalamMovie> all() {
        if (cache != null) return cache;
        if (apiKey == null || apiKey.isBlank()) return new ArrayList<>();

        List<MalayalamMovie> list = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int page = 1; page <= 5; page++) {
            try {
                String url = "https://api.themoviedb.org/3/discover/movie?api_key=" + apiKey
                        + "&with_original_language=ml&sort_by=popularity.desc"
                        + "&vote_count.gte=10&page=" + page;
                Map<String, Object> body = fetchPage(url);
                if (body == null) break;
                List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
                if (results == null || results.isEmpty()) break;

                for (Map<String, Object> m : results) {
                    long id = ((Number) m.get("id")).longValue();
                    if (!seen.add(id)) continue;

                    String title = (String) m.get("title");
                    String date = (String) m.get("release_date");
                    String year = (date != null && date.length() >= 4) ? date.substring(0, 4) : "";
                    String overview = (String) m.get("overview");
                    String poster = (String) m.get("poster_path");
                    String posterUrl = poster != null
                            ? "https://image.tmdb.org/t/p/w342" + poster : "/no-poster.svg";
                    double rating = m.get("vote_average") == null
                            ? 0 : ((Number) m.get("vote_average")).doubleValue();
                    int votes = m.get("vote_count") == null
                            ? 0 : ((Number) m.get("vote_count")).intValue();

                    List<String> genres = new ArrayList<>();
                    List<Number> ids = (List<Number>) m.get("genre_ids");
                    if (ids != null) {
                        for (Number g : ids) {
                            String name = GENRES.get(g.intValue());
                            if (name != null) genres.add(name);
                        }
                    }
                    list.add(new MalayalamMovie(id, title, year, overview, posterUrl,
                            rating, votes, genres, rating));
                }
            } catch (Exception e) {
                System.out.println("TMDB request failed: " + e.getMessage());
                break;
            }
        }
        if (!list.isEmpty()) cache = list; // only cache a successful load
        return list;
    }
    // Tries up to 3 times, because the network on the free server can be unreliable
    private Map<String, Object> fetchPage(String url) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return rest.get().uri(url).retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                last = e;
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                System.out.println("TMDB attempt " + attempt + " failed: " + root);
                Thread.sleep(1500);
            }
        }
        throw last;
    }
    public List<MalayalamMovie> search(String q, String sort) {
        List<MalayalamMovie> list = new ArrayList<>(all());
        if (q != null && !q.isBlank()) {
            String key = q.toLowerCase();
            list = list.stream()
                    .filter(m -> m.getTitle() != null && m.getTitle().toLowerCase().contains(key))
                    .collect(Collectors.toList());
        }
        if ("rating".equals(sort)) {
            list.sort((a, b) -> Double.compare(b.getRating(), a.getRating()));
        }
        return list;
    }

    public MalayalamMovie find(long id) {
        for (MalayalamMovie m : all()) {
            if (m.getId() == id) return m;
        }
        return null;
    }

    // Content-based similarity: 80% genre overlap (Jaccard) + 20% TMDB rating
    public List<MalayalamMovie> similar(long id, int n) {
        MalayalamMovie target = find(id);
        if (target == null) return new ArrayList<>();
        Set<String> tg = new HashSet<>(target.getGenres());

        List<MalayalamMovie> result = new ArrayList<>();
        for (MalayalamMovie m : all()) {
            if (m.getId() == id) continue;
            Set<String> inter = new HashSet<>(tg);
            inter.retainAll(m.getGenres());
            Set<String> union = new HashSet<>(tg);
            union.addAll(m.getGenres());
            double jaccard = union.isEmpty() ? 0 : (double) inter.size() / union.size();
            double score = (0.8 * jaccard + 0.2 * (m.getRating() / 10.0)) * 100;
            result.add(new MalayalamMovie(m.getId(), m.getTitle(), m.getYear(), m.getOverview(),
                    m.getPosterUrl(), m.getRating(), m.getVotes(), m.getGenres(), score));
        }
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return result.stream().limit(n).collect(Collectors.toList());
    }
}