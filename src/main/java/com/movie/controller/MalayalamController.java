package com.movie.controller;

import com.movie.model.MalayalamMovie;
import com.movie.service.MalayalamService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MalayalamController {

    private final MalayalamService service;

    public MalayalamController(MalayalamService service) {
        this.service = service;
    }

    @GetMapping("/malayalam")
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "popular") String sort,
                       Model model) {
        model.addAttribute("movies", service.search(q, sort));
        model.addAttribute("q", q);
        model.addAttribute("sort", sort);
        return "malayalam";
    }

    @GetMapping("/malayalam/{id}")
    public String detail(@PathVariable long id, Model model) {
        MalayalamMovie movie = service.find(id);
        if (movie == null) return "redirect:/malayalam";
        model.addAttribute("movie", movie);
        model.addAttribute("similar", service.similar(id, 12));
        return "malayalam-detail";
    }
}