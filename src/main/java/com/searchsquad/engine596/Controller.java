package com.searchsquad.engine596;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @GetMapping("/index")
    public void search() {
        StandardAnalyzer analyzer = new StandardAnalyzer();
        return;
    }

}
