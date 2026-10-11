package com.movie.controller;

import com.movie.service.EvaluationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @GetMapping(value = "/evaluate", produces = "text/plain;charset=UTF-8")
    public String evaluate() {
        return service.evaluate();
    }
}