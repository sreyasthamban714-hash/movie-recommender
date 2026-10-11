package com.movie.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PosterService {

    @Value("${tmdb.api.key:}")
    private String apiKey;

    private final Map<Long, Long> tmdbIds = new ConcurrentHashMap<>();
    private final Map<Long, String> cache = new ConcurrentHashMap<>();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final Pattern posterPattern = Pattern.compile("\"poster_path\"\\s*:\\s*\"([^\"]+)\"");
    private boolean linksLoaded = false;

    // links.csv columns: movieId, imdbId, tmdbId
    private synchronized void loadLinks() {
        if (linksLoaded) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/links.csv").getInputStream()))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length >= 3 && !p[2].isBlank()) {
                    tmdbIds.put(Long.parseLong(p[0]), Long.parseLong(p[2].trim()));
                }
            }
        } catch (Exception e) {
            System.out.println("Could not read links.csv: " + e.getMessage());
        }
        linksLoaded = true;
    }

    // Returns the poster image URL, or null if there is none
    public String getPosterUrl(long movieId) {
        if (apiKey == null || apiKey.isBlank()) return null;
        loadLinks();

        String cached = cache.get(movieId);
        if (cached != null) return cached.isEmpty() ? null : cached;

        Long tmdbId = tmdbIds.get(movieId);
        if (tmdbId == null) return null;

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.themoviedb.org/3/movie/" + tmdbId
                            + "?api_key=" + apiKey))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                Matcher m = posterPattern.matcher(res.body());
                if (m.find()) {
                    String path = m.group(1).replace("\\/", "/");
                    String url = "https://image.tmdb.org/t/p/w342" + path;
                    cache.put(movieId, url);
                    return url;
                }
                cache.put(movieId, ""); // this movie has no poster
            }
        } catch (Exception e) {
            // network problem: don't cache, try again next time
        }
        return null;
    }
}