package com.movie.controller;

import com.movie.service.PosterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class PosterController {

    private final PosterService posterService;

    public PosterController(PosterService posterService) {
        this.posterService = posterService;
    }

    @GetMapping("/poster/{id}")
    public ResponseEntity<Void> poster(@PathVariable long id) {
        String url = posterService.getPosterUrl(id);
        boolean found = url != null;
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(found ? url : "/no-poster.svg"))
                .header("Cache-Control", found ? "max-age=86400" : "no-cache")
                .build();
    }
}