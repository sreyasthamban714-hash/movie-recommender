package com.movie.service;

import com.movie.model.Rating;
import com.movie.repository.RatingRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvaluationService {

    private final RatingRepository ratingRepo;

    private Map<Integer, Map<Long, Double>> userRatings;
    private Map<Long, Map<Integer, Double>> movieRatings;
    private Map<Long, Double> norms;
    private Map<Long, Double> movieMean;
    private Map<Integer, Double> userMean;
    private double globalMean;

    public EvaluationService(RatingRepository ratingRepo) {
        this.ratingRepo = ratingRepo;
    }

    public synchronized String evaluate() {
        // 80% train / 20% test split with a fixed seed so results are repeatable
        List<Rating> all = new ArrayList<>(ratingRepo.findAll());
        Collections.shuffle(all, new Random(42));
        int split = (int) (all.size() * 0.8);
        List<Rating> train = all.subList(0, split);
        List<Rating> test = all.subList(split, all.size());

        buildTrainingData(train);

        // ---------- Part 1: rating prediction (RMSE, MAE) ----------
        double seG = 0, seM = 0, seC = 0, aeG = 0, aeM = 0, aeC = 0;
        for (Rating r : test) {
            double actual = r.getRating();
            double pG = globalMean;
            double pM = baseline(r.getUserId(), r.getMovieId());
            double pC = predictCF(r.getUserId(), r.getMovieId());
            seG += (actual - pG) * (actual - pG);
            seM += (actual - pM) * (actual - pM);
            seC += (actual - pC) * (actual - pC);
            aeG += Math.abs(actual - pG);
            aeM += Math.abs(actual - pM);
            aeC += Math.abs(actual - pC);
        }
        int n = test.size();

        // ---------- Part 2: top-10 recommendations (Precision, Recall) ----------
        // "relevant" = movies the user rated 4 or more in the hidden test set
        Map<Integer, Set<Long>> relevant = new HashMap<>();
        for (Rating r : test) {
            if (r.getRating() >= 4.0) {
                relevant.computeIfAbsent(r.getUserId(), k -> new HashSet<>()).add(r.getMovieId());
            }
        }
        List<Integer> users = new ArrayList<>(relevant.keySet());
        Collections.sort(users);
        if (users.size() > 100) users = users.subList(0, 100); // first 100 users

        // popularity baseline: most-rated movies first
        List<Long> popular = movieRatings.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        double pPop = 0, rPop = 0, pCf = 0, rCf = 0;
        for (int u : users) {
            Set<Long> rel = relevant.get(u);
            Map<Long, Double> seen = userRatings.getOrDefault(u, Collections.emptyMap());

            List<Long> popTop = new ArrayList<>();
            for (Long m : popular) {
                if (popTop.size() == 10) break;
                if (!seen.containsKey(m)) popTop.add(m);
            }
            List<Long> cfTop = recommendTop(u, 10);

            int hitsPop = 0, hitsCf = 0;
            for (Long m : popTop) if (rel.contains(m)) hitsPop++;
            for (Long m : cfTop) if (rel.contains(m)) hitsCf++;

            pPop += hitsPop / 10.0;
            rPop += (double) hitsPop / rel.size();
            pCf += hitsCf / 10.0;
            rCf += (double) hitsCf / rel.size();
        }
        int nu = users.size();

        // ---------- Build the result text ----------
        StringBuilder sb = new StringBuilder();
        sb.append("Train/test split: 80% / 20% (random seed 42)\n");
        sb.append(String.format(Locale.US, "Train ratings: %d | Test ratings: %d%n%n",
                train.size(), test.size()));

        sb.append("RATING PREDICTION (lower is better)\n");
        sb.append(String.format(Locale.US, "%-22s %8s %8s%n", "Method", "RMSE", "MAE"));
        sb.append(String.format(Locale.US, "%-22s %8.4f %8.4f%n", "Global average",
                Math.sqrt(seG / n), aeG / n));
        sb.append(String.format(Locale.US, "%-22s %8.4f %8.4f%n", "Movie average",
                Math.sqrt(seM / n), aeM / n));
        sb.append(String.format(Locale.US, "%-22s %8.4f %8.4f%n%n", "Item-based CF",
                Math.sqrt(seC / n), aeC / n));

        sb.append("TOP-10 RECOMMENDATION (" + nu + " sample users, higher is better)\n");
        sb.append(String.format(Locale.US, "%-22s %14s %12s%n", "Method", "Precision@10", "Recall@10"));
        sb.append(String.format(Locale.US, "%-22s %14.4f %12.4f%n", "Popularity baseline",
                pPop / nu, rPop / nu));
        sb.append(String.format(Locale.US, "%-22s %14.4f %12.4f%n", "Item-based CF",
                pCf / nu, rCf / nu));

        String result = sb.toString();
        System.out.println(result);
        return result;
    }

    // ---------- helpers ----------

    private void buildTrainingData(List<Rating> train) {
        userRatings = new HashMap<>();
        movieRatings = new HashMap<>();
        norms = new HashMap<>();
        movieMean = new HashMap<>();
        userMean = new HashMap<>();

        double total = 0;
        for (Rating r : train) {
            userRatings.computeIfAbsent(r.getUserId(), k -> new HashMap<>())
                    .put(r.getMovieId(), r.getRating());
            movieRatings.computeIfAbsent(r.getMovieId(), k -> new HashMap<>())
                    .put(r.getUserId(), r.getRating());
            total += r.getRating();
        }
        globalMean = total / train.size();

        for (Map.Entry<Long, Map<Integer, Double>> e : movieRatings.entrySet()) {
            double sum = 0, sq = 0;
            for (double v : e.getValue().values()) {
                sum += v;
                sq += v * v;
            }
            movieMean.put(e.getKey(), sum / e.getValue().size());
            norms.put(e.getKey(), Math.sqrt(sq));
        }
        for (Map.Entry<Integer, Map<Long, Double>> e : userRatings.entrySet()) {
            double sum = 0;
            for (double v : e.getValue().values()) sum += v;
            userMean.put(e.getKey(), sum / e.getValue().size());
        }
    }

    // Movie average; falls back to the user's average, then the global average
    private double baseline(int userId, long movieId) {
        return movieMean.getOrDefault(movieId, userMean.getOrDefault(userId, globalMean));
    }

    // Item-based CF: movie average + weighted average of the user's deviations
    // on the 20 most similar movies the user has rated
    private double predictCF(int userId, long movieId) {
        double base = baseline(userId, movieId);
        Map<Long, Double> mine = userRatings.get(userId);
        if (mine == null || !movieRatings.containsKey(movieId)) return base;

        List<double[]> neighbours = new ArrayList<>();
        for (Map.Entry<Long, Double> e : mine.entrySet()) {
            double sim = similarity(movieId, e.getKey());
            if (sim > 0) {
                neighbours.add(new double[]{sim, e.getValue() - movieMean.get(e.getKey())});
            }
        }
        if (neighbours.isEmpty()) return base;

        neighbours.sort((a, b) -> Double.compare(b[0], a[0]));
        double num = 0, den = 0;
        for (int i = 0; i < Math.min(20, neighbours.size()); i++) {
            num += neighbours.get(i)[0] * neighbours.get(i)[1];
            den += neighbours.get(i)[0];
        }
        return Math.max(0.5, Math.min(5.0, base + num / den));
    }

    private double similarity(long a, long b) {
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

    // Top-n movie ids for a user, using only the training data
    private List<Long> recommendTop(int userId, int n) {
        Map<Long, Double> mine = userRatings.get(userId);
        if (mine == null) return new ArrayList<>();

        List<Map.Entry<Long, Double>> liked = mine.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(30)
                .collect(Collectors.toList());

        List<Map.Entry<Long, Double>> scored = new ArrayList<>();
        for (Map.Entry<Long, Map<Integer, Double>> c : movieRatings.entrySet()) {
            if (mine.containsKey(c.getKey()) || c.getValue().size() < 5) continue;
            double score = 0;
            for (Map.Entry<Long, Double> l : liked) {
                score += similarity(c.getKey(), l.getKey()) * l.getValue();
            }
            if (score > 0) scored.add(new AbstractMap.SimpleEntry<>(c.getKey(), score));
        }
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return scored.stream().limit(n).map(Map.Entry::getKey).collect(Collectors.toList());
    }
}