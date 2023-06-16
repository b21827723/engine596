package com.searchsquad.engine596.DTO;

import lombok.Data;

@Data
public class SearchResult {
    private String id;
    private String title;
    private String abstractText;
    private String text;
    private float rank;
}
