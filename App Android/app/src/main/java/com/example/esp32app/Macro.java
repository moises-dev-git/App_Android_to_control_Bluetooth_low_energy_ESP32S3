package com.example.esp32app;

public class Macro {
    private String name;
    private String content;

    public Macro(String name, String content) {
        this.name = name;
        this.content = content;
    }

    public String getName() { return name; }
    public String getContent() { return content; }

    @Override
    public String toString() {
        return name; // Useful for Spinner adapter
    }
}
