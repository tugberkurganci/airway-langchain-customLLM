package com.example.demo.langchain4j;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

enum Role {

    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    @JsonValue
    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }
}