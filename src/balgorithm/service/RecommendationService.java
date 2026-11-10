package balgorithm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RecommendationService {
    private static final Set<String> WEATHER_OPTIONS =
            Set.of("Sunny", "Hot", "Moderate", "Cold", "Rainy", "Humid", "Snowy");
    private static final Set<String> ACTIVITY_OPTIONS =
            Set.of("Sightseeing", "Trekking", "Beach", "Swimming", "Camping", "Business", "Casual");

    public List<Map<String, Object>> recommend(String destination, String weather, String activity, int days) {
        String cleanDestination = destination == null ? "" : destination.trim();
        if (cleanDestination.isBlank() || cleanDestination.length() > 100) {
            throw new IllegalArgumentException("Enter a destination between 1 and 100 characters.");
        }
        String selectedWeather = selectOption(weather, WEATHER_OPTIONS, "weather");
        String selectedActivity = selectOption(activity, ACTIVITY_OPTIONS, "activity");
        if (days < 1 || days > 365) throw new IllegalArgumentException("Trip length must be 1–365 days.");

        List<Map<String, Object>> suggestions = new ArrayList<>();
        add(suggestions, "Comfortable everyday tops", Math.min(days, 5), 180, "MEDIUM", "Essentials");
        add(suggestions, "Versatile travel trousers", Math.max(1, Math.min(days, 3)), 350, "MEDIUM", "Essentials");
        add(suggestions, "Comfortable walking shoes", 1, 650, "MEDIUM", "Essentials");
        add(suggestions, "Optional extra pair of socks", 1, 80, "LOW", "Essentials");

        addWeatherSuggestions(suggestions, selectedWeather, days);
        addActivitySuggestions(suggestions, selectedActivity, days);
        addDestinationSuggestions(suggestions, cleanDestination, selectedWeather);

        String outfitDestination = cleanDestination.length() > 78
                ? cleanDestination.substring(0, 78).trim() : cleanDestination;
        add(suggestions, "Outfit planned for " + outfitDestination, 1, 300, "MEDIUM", "Destination");
        return suggestions;
    }

    private String selectOption(String selection, Set<String> options, String fieldName) {
        if (selection != null) {
            String cleanSelection = selection.trim();
            for (String option : options) {
                if (option.equalsIgnoreCase(cleanSelection)) return option;
            }
        }
        throw new IllegalArgumentException("Choose a valid " + fieldName + " option.");
    }

    private void addWeatherSuggestions(List<Map<String, Object>> suggestions, String weather, int days) {
        switch (weather) {
            case "Sunny" -> {
                add(suggestions, "Breathable sun-protective shirt", 2, 170, "HIGH", "Weather");
                add(suggestions, "Lightweight sun hat", 1, 90, "MEDIUM", "Weather");
            }
            case "Hot" -> {
                add(suggestions, "Lightweight breathable shirts", Math.min(days, 4), 140, "HIGH", "Weather");
                add(suggestions, "Lightweight shorts", 2, 180, "HIGH", "Weather");
            }
            case "Moderate" -> {
                add(suggestions, "Comfortable everyday clothing", Math.min(days, 3), 280, "MEDIUM", "Weather");
                add(suggestions, "Light layering cardigan", 1, 220, "MEDIUM", "Weather");
                add(suggestions, "Comfortable everyday footwear", 1, 420, "MEDIUM", "Weather");
            }
            case "Cold" -> {
                add(suggestions, "Warm insulating jacket", 1, 650, "HIGH", "Weather");
                add(suggestions, "Thermal base layers", 2, 220, "HIGH", "Weather");
                add(suggestions, "Warm scarf", 1, 120, "MEDIUM", "Weather");
            }
            case "Rainy" -> {
                add(suggestions, "Waterproof rain jacket", 1, 450, "HIGH", "Weather");
                add(suggestions, "Quick-dry trousers", 1, 300, "MEDIUM", "Weather");
                add(suggestions, "Waterproof shoe covers", 1, 180, "MEDIUM", "Weather");
            }
            case "Humid" -> {
                add(suggestions, "Moisture-wicking shirts", Math.min(days, 4), 150, "HIGH", "Weather");
                add(suggestions, "Lightweight quick-dry trousers", 1, 260, "MEDIUM", "Weather");
            }
            case "Snowy" -> {
                add(suggestions, "Insulated winter jacket", 1, 800, "HIGH", "Weather");
                add(suggestions, "Thermal base layers", 2, 240, "HIGH", "Weather");
                add(suggestions, "Insulated waterproof boots", 1, 900, "HIGH", "Weather");
                add(suggestions, "Warm gloves", 1, 100, "MEDIUM", "Weather");
            }
            default -> throw new IllegalArgumentException("Choose a valid weather option.");
        }
    }

    private void addActivitySuggestions(List<Map<String, Object>> suggestions, String activity, int days) {
        switch (activity) {
            case "Sightseeing" -> {
                add(suggestions, "All-day walking shoes", 1, 550, "HIGH", "Activity");
                add(suggestions, "Comfortable sightseeing outfit", 1, 350, "MEDIUM", "Activity");
            }
            case "Trekking" -> {
                add(suggestions, "Hiking boots with good grip", 1, 850, "HIGH", "Activity");
                add(suggestions, "Moisture-wicking hiking trousers", 1, 300, "HIGH", "Activity");
                add(suggestions, "Lightweight hiking base layer", 2, 160, "MEDIUM", "Activity");
            }
            case "Beach" -> {
                add(suggestions, "Swimwear", 1, 220, "HIGH", "Activity");
                add(suggestions, "Lightweight beach cover-up", 1, 180, "HIGH", "Activity");
                add(suggestions, "Breathable sandals", 1, 250, "MEDIUM", "Activity");
            }
            case "Swimming" -> {
                add(suggestions, "Swimwear", 1, 220, "HIGH", "Activity");
                add(suggestions, "Quick-dry change of clothes", 1, 250, "MEDIUM", "Activity");
            }
            case "Camping" -> {
                add(suggestions, "Warm camping layers", 1, 350, "HIGH", "Activity");
                add(suggestions, "Rugged outdoor trousers", 1, 400, "HIGH", "Activity");
                add(suggestions, "Sturdy outdoor boots", 1, 800, "HIGH", "Activity");
            }
            case "Business" -> {
                add(suggestions, "Smart business outfit", 1, 500, "HIGH", "Activity");
                add(suggestions, "Business shoes", 1, 600, "HIGH", "Activity");
                add(suggestions, "Wrinkle-resistant shirt", 2, 180, "MEDIUM", "Activity");
            }
            case "Casual" -> {
                add(suggestions, "Casual mix-and-match outfits", Math.min(days, 3), 320, "MEDIUM", "Activity");
                add(suggestions, "Everyday comfortable shoes", 1, 450, "MEDIUM", "Activity");
            }
            default -> throw new IllegalArgumentException("Choose a valid activity option.");
        }
    }

    private void addDestinationSuggestions(List<Map<String, Object>> suggestions, String destination, String weather) {
        String normalized = normalizeDestination(destination);

        if (matches(normalized, "kerala", "kochi", "cochin", "thiruvananthapuram", "trivandrum")) {
            if (!weather.equals("Cold") && !weather.equals("Snowy")) {
                add(suggestions, "Lightweight breathable Kerala clothing", 2, 160, "HIGH", "Kerala");
                if (weather.equals("Rainy") || weather.equals("Humid")) {
                    add(suggestions, "Quick-dry layer for Kerala showers", 1, 220, "MEDIUM", "Kerala");
                }
            }
        } else if (matches(normalized, "goa")) {
            if (!weather.equals("Cold") && !weather.equals("Snowy")) {
                add(suggestions, "Lightweight Goa beachwear", 1, 200, "HIGH", "Goa");
                add(suggestions, "Breathable coastal sandals", 1, 230, "MEDIUM", "Goa");
            }
        } else if (matches(normalized, "ooty", "udhagamandalam")) {
            if (weather.equals("Cold") || weather.equals("Snowy")) {
                add(suggestions, "Warm Ooty hill-station sweater", 1, 300, "HIGH", "Ooty");
            } else {
                add(suggestions, "Light Ooty evening sweater", 1, 260, "MEDIUM", "Ooty");
            }
            if (weather.equals("Rainy") || weather.equals("Humid")) {
                add(suggestions, "Water-resistant Ooty outer layer", 1, 350, "HIGH", "Ooty");
            }
        } else if (matches(normalized, "manali")) {
            if (weather.equals("Cold") || weather.equals("Snowy")) {
                add(suggestions, "Insulated Manali mountain layer", 1, 450, "HIGH", "Manali");
                add(suggestions, "Warm mountain socks", 2, 90, "MEDIUM", "Manali");
            } else {
                add(suggestions, "Lightweight Manali layering jacket", 1, 320, "MEDIUM", "Manali");
            }
        } else if (matches(normalized, "delhi", "new delhi")) {
            if (weather.equals("Hot") || weather.equals("Sunny")) {
                add(suggestions, "Breathable Delhi city outfit", 1, 250, "HIGH", "Delhi");
            } else if (weather.equals("Cold") || weather.equals("Snowy")) {
                add(suggestions, "Warm Delhi evening layer", 1, 300, "HIGH", "Delhi");
            } else {
                add(suggestions, "Light Delhi city layer", 1, 220, "MEDIUM", "Delhi");
            }
        } else if (matches(normalized, "mumbai", "bombay")) {
            if (!weather.equals("Cold") && !weather.equals("Snowy")) {
                add(suggestions, "Breathable Mumbai coastal outfit", 1, 240, "HIGH", "Mumbai");
                if (weather.equals("Rainy") || weather.equals("Humid")) {
                    add(suggestions, "Quick-dry Mumbai rain layer", 1, 260, "MEDIUM", "Mumbai");
                }
            }
        } else if (matches(normalized, "chennai", "madras")) {
            if (!weather.equals("Cold") && !weather.equals("Snowy")) {
                add(suggestions, "Lightweight Chennai cotton outfit", 1, 220, "HIGH", "Chennai");
            }
        } else if (matches(normalized, "bengaluru", "bangalore")) {
            if (weather.equals("Cold") || weather.equals("Snowy")) {
                add(suggestions, "Warm Bengaluru evening layer", 1, 280, "HIGH", "Bengaluru");
            } else {
                add(suggestions, "Light Bengaluru layering jacket", 1, 260, "MEDIUM", "Bengaluru");
            }
        } else if (matches(normalized, "coast", "coastal", "island", "bali", "phuket", "maldives", "hawaii")) {
            if (!weather.equals("Cold") && !weather.equals("Snowy")) {
                add(suggestions, "Lightweight coastal cover-up", 1, 170, "MEDIUM", "Destination");
            }
        } else if (matches(normalized, "mountain", "mountains", "himalaya", "ladakh", "shimla",
                "alps", "nepal", "srinagar")) {
            if (weather.equals("Cold") || weather.equals("Snowy")) {
                add(suggestions, "Extra insulating mountain layer", 1, 280, "HIGH", "Destination");
            } else {
                add(suggestions, "Lightweight mountain layering jacket", 1, 300, "MEDIUM", "Destination");
            }
        }
    }

    private String normalizeDestination(String destination) {
        return " " + destination.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim() + " ";
    }

    private boolean matches(String normalizedDestination, String... names) {
        for (String name : names) {
            String normalizedName = " " + name.toLowerCase(Locale.ROOT) + " ";
            if (normalizedDestination.contains(normalizedName)) return true;
        }
        return false;
    }

    private void add(List<Map<String, Object>> suggestions, String name, int quantity, int weight,
                     String priority, String source) {
        add(suggestions, name, quantity, weight, priority, source, "CLOTHING");
    }

    private void add(List<Map<String, Object>> suggestions, String name, int quantity, int weight,
                     String priority, String source, String category) {
        for (Map<String, Object> suggestion : suggestions) {
            if (category.equals(suggestion.get("category"))
                    && name.equalsIgnoreCase((String) suggestion.get("name"))) return;
        }
        suggestions.add(Map.of("name", name, "category", category, "quantity", quantity,
                "weight", weight, "price", 0, "priority", priority, "source", source));
    }
}
