package com.arcube.transferaggregator.domain;

// Amenity - included or available for extra charge
public record Amenity(
    String key,
    String name,
    String description,
    String image,
    boolean included,
    Money price
) {
    public static Amenity included(String key, String name) {
        return new Amenity(key, name, null, null, true, null);
    }
    
    public static Amenity available(String key, String name, Money price) {
        return new Amenity(key, name, null, null, false, price);
    }
}
