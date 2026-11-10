package balgorithm.server;

import balgorithm.model.PackingItem;
import balgorithm.model.OutfitPlan;
import balgorithm.model.OutfitPlan.OutfitDay;
import balgorithm.model.OutfitPlan.OutfitItem;
import balgorithm.model.Trip;
import balgorithm.model.User;
import balgorithm.service.AuthService;
import balgorithm.service.ItemService;
import balgorithm.service.OutfitPlanService;
import balgorithm.service.RecommendationService;
import balgorithm.service.TripService;
import balgorithm.service.WeightLimitService;
import balgorithm.storage.FileStorage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class BalgorithmServer {
    private static final int MAX_REQUEST_BYTES = 1_000_000;

    private final AuthService authService;
    private final TripService tripService;
    private final ItemService itemService;
    private final OutfitPlanService outfitPlanService;
    private final WeightLimitService weightLimitService;
    private final FileStorage storage;
    private final RecommendationService recommendationService = new RecommendationService();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final Path frontendDirectory;

    private BalgorithmServer(Path projectDirectory) throws IOException {
        Path dataDirectory = configuredDirectory("BAGORITHM_DATA_DIR", projectDirectory, "data");
        storage = new FileStorage(dataDirectory);
        authService = new AuthService(storage);
        tripService = new TripService(storage);
        itemService = new ItemService(storage, tripService);
        outfitPlanService = new OutfitPlanService(storage, tripService, itemService);
        weightLimitService = new WeightLimitService(storage, tripService);
        frontendDirectory = configuredDirectory("BAGORITHM_FRONTEND_DIR", projectDirectory, "frontend");
        if (!Files.isDirectory(frontendDirectory)) {
            throw new IOException("Frontend directory not found: " + frontendDirectory
                    + ". Set BAGORITHM_FRONTEND_DIR to its location.");
        }
    }

    public static void main(String[] args) throws IOException {
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        int port = readPort();
        BalgorithmServer application = new BalgorithmServer(projectDirectory);
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", application::handle);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        System.out.println("BAGORITHM is running at http://localhost:" + port);
        System.out.println("Saved text files are in "
                + configuredDirectory("BAGORITHM_DATA_DIR", projectDirectory, "data"));
    }

    private static int readPort() {
        String configuredPort = System.getenv("PORT");
        if (configuredPort == null || configuredPort.isBlank()) return 8080;
        try {
            int port = Integer.parseInt(configuredPort);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("PORT must be a number from 1 to 65535.");
        }
    }

    private static Path configuredDirectory(String variable, Path projectDirectory, String defaultDirectory) {
        String configured = System.getenv(variable);
        Path directory = configured == null || configured.isBlank()
                ? projectDirectory.resolve(defaultDirectory) : Path.of(configured.trim());
        if (!directory.isAbsolute()) directory = projectDirectory.resolve(directory);
        return directory.toAbsolutePath().normalize();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else if (exchange.getRequestMethod().equals("GET")) {
                serveFrontend(exchange, path);
            } else {
                sendError(exchange, 404, "Page not found.");
            }
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (UnauthorizedException exception) {
            sendError(exchange, 401, exception.getMessage());
        } catch (IOException exception) {
            System.err.println("BAGORITHM storage or request error: " + exception.getMessage());
            sendError(exchange, 500, "The request could not be completed. Check the server console for details.");
        } catch (Exception exception) {
            System.err.println("BAGORITHM server error: " + exception.getMessage());
            sendError(exchange, 500, "The request could not be completed. Check the server console for details.");
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();
        String suffix = path.substring("/api/".length());
        if (suffix.equals("register") && method.equals("POST")) {
            Map<String, Object> input = readObject(exchange);
            User user = authService.register(string(input, "name"), string(input, "email"),
                    string(input, "password"));
            sendJson(exchange, 201, sessionResponse(user));
            return;
        }
        if (suffix.equals("login") && method.equals("POST")) {
            Map<String, Object> input = readObject(exchange);
            User user = authService.login(string(input, "email"), string(input, "password"));
            sendJson(exchange, 200, sessionResponse(user));
            return;
        }
        if (suffix.equals("logout") && method.equals("POST")) {
            String token = bearerToken(exchange);
            if (token != null) sessions.remove(token);
            sendJson(exchange, 200, Map.of("message", "Signed out."));
            return;
        }
        if (suffix.equals("session") && method.equals("GET")) {
            User user = authenticatedUser(exchange);
            sendJson(exchange, 200, Map.of("user", userJson(user)));
            return;
        }

        User user = authenticatedUser(exchange);
        if (suffix.equals("trips")) {
            handleTrips(exchange, method, user);
        } else if (suffix.startsWith("trips/")) {
            handleTrip(exchange, method, user, suffix.substring("trips/".length()));
        } else if (suffix.equals("items")) {
            handleItems(exchange, method, user);
        } else if (suffix.startsWith("items/")) {
            handleItem(exchange, method, user, suffix.substring("items/".length()));
        } else if (suffix.equals("packing-limit")) {
            handlePackingLimit(exchange, method, user);
        } else if (suffix.equals("outfit-plans/generate") && method.equals("POST")) {
            Map<String, Object> input = readObject(exchange);
            sendJson(exchange, 200, outfitPlanJson(outfitPlanService.generate(user.getId(),
                    string(input, "tripId"), string(input, "destination"), string(input, "weather"),
                    stringList(input, "activities"), stringList(input, "selectedItemIds"))));
        } else if (suffix.equals("outfit-plans")) {
            handleOutfitPlans(exchange, method, user);
        } else if (suffix.equals("recommendations") && method.equals("GET")) {
            String tripId = queryParameter(exchange, "tripId");
            Trip trip = tripService.findForUser(tripId, user.getId());
            sendJson(exchange, 200, recommendationService.recommend(
                    queryParameter(exchange, "destination"),
                    queryParameter(exchange, "weather"),
                    queryParameter(exchange, "activity"),
                    trip.getDays()));
        } else {
            sendError(exchange, 404, "API endpoint not found.");
        }
    }

    private void handlePackingLimit(HttpExchange exchange, String method, User user) throws IOException {
        if (method.equals("GET")) {
            String tripId = queryParameter(exchange, "tripId");
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("weightLimit", weightLimitService.getForTrip(tripId, user.getId()));
            sendJson(exchange, 200, response);
            return;
        }
        if (method.equals("PUT")) {
            Map<String, Object> input = readObject(exchange);
            double limit = weightLimitService.saveForTrip(string(input, "tripId"), user.getId(),
                    number(input, "weightLimit"));
            sendJson(exchange, 200, Map.of("weightLimit", limit));
            return;
        }
        sendError(exchange, 405, "Use GET or PUT for the luggage weight limit.");
    }

    private void handleOutfitPlans(HttpExchange exchange, String method, User user) throws IOException {
        String tripId = queryParameter(exchange, "tripId");
        if (method.equals("GET")) {
            sendJson(exchange, 200, outfitPlanService.listForTrip(tripId, user.getId())
                    .stream().map(this::outfitPlanJson).toList());
            return;
        }
        if (method.equals("POST")) {
            Map<String, Object> input = readObject(exchange);
            String bodyTripId = string(input, "tripId");
            if (!tripId.isBlank() && !tripId.equals(bodyTripId)) {
                throw new IllegalArgumentException("The outfit plan trip does not match the request.");
            }
            sendJson(exchange, 201, outfitPlanJson(outfitPlanService.save(user.getId(), bodyTripId,
                    string(input, "destination"), string(input, "weather"), outfitDays(input))));
            return;
        }
        sendError(exchange, 405, "Use GET or POST for outfit plans.");
    }

    private List<OutfitDay> outfitDays(Map<String, Object> input) {
        Object value = input.get("days");
        if (!(value instanceof List<?> rawDays)) {
            throw new IllegalArgumentException("Missing or invalid outfit days.");
        }
        List<OutfitDay> days = new java.util.ArrayList<>();
        for (Object rawDay : rawDays) {
            if (!(rawDay instanceof Map<?, ?> day)) {
                throw new IllegalArgumentException("Each outfit day must be an object.");
            }
            Map<String, Object> dayInput = stringKeyedMap(day);
            Object rawItems = dayInput.get("items");
            if (!(rawItems instanceof List<?> itemList)) {
                throw new IllegalArgumentException("Each outfit day must include clothing items.");
            }
            List<OutfitItem> items = new java.util.ArrayList<>();
            for (Object rawItem : itemList) {
                if (!(rawItem instanceof Map<?, ?> item)) {
                    throw new IllegalArgumentException("Each outfit item must be an object.");
                }
                Map<String, Object> itemInput = stringKeyedMap(item);
                items.add(new OutfitItem(string(itemInput, "category"), string(itemInput, "name"),
                        number(itemInput, "weight")));
            }
            days.add(new OutfitDay(integer(dayInput, "day"), string(dayInput, "activity"), items));
        }
        return days;
    }

    private List<String> stringList(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (!(value instanceof List<?> rawValues)) {
            throw new IllegalArgumentException("Missing or invalid " + key + ".");
        }
        List<String> values = new java.util.ArrayList<>();
        for (Object entry : rawValues) {
            if (!(entry instanceof String text)) {
                throw new IllegalArgumentException("Missing or invalid " + key + ".");
            }
            values.add(text);
        }
        return values;
    }

    private Map<String, Object> stringKeyedMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Invalid object field.");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private String optionalString(Map<String, Object> input, String key, String fallback) {
        Object value = input.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String text)) throw new IllegalArgumentException("Invalid " + key + ".");
        return text;
    }

    private Double optionalNumber(Map<String, Object> input, String key) {
        if (!input.containsKey(key)) return null;
        return number(input, key);
    }

    private void validateWeightLimit(double weightLimit) {
        if (!Double.isFinite(weightLimit) || weightLimit < 1 || weightLimit > 100_000) {
            throw new IllegalArgumentException("Luggage limit must be between 1 and 100,000 grams.");
        }
    }

    private Map<String, Object> outfitPlanJson(OutfitPlan plan) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", plan.getId());
        result.put("tripId", plan.getTripId());
        result.put("destination", plan.getDestination());
        result.put("weather", plan.getWeather());
        result.put("days", plan.getDays().stream().map(day -> {
            Map<String, Object> dayResult = new LinkedHashMap<>();
            dayResult.put("day", day.getDayNumber());
            dayResult.put("activity", day.getActivity());
            dayResult.put("items", day.getItems().stream().map(item -> {
                Map<String, Object> itemResult = new LinkedHashMap<>();
                itemResult.put("category", item.getCategory());
                itemResult.put("name", item.getName());
                itemResult.put("weight", item.getWeight());
                return itemResult;
            }).toList());
            dayResult.put("totalWeight", day.getTotalWeight());
            return dayResult;
        }).toList());
        return result;
    }

    private void handleTrips(HttpExchange exchange, String method, User user) throws IOException {
        if (method.equals("GET")) {
            List<Map<String, Object>> trips = new java.util.ArrayList<>();
            for (Trip trip : tripService.listForUser(user.getId())) {
                Map<String, Object> result = tripJson(trip);
                result.put("weightLimit", weightLimitService.getForTrip(trip.getId(), user.getId()));
                trips.add(result);
            }
            sendJson(exchange, 200, trips);
            return;
        }
        if (method.equals("POST")) {
            Map<String, Object> input = readObject(exchange);
            String destination = string(input, "destination");
            String name = optionalString(input, "name", destination);
            double weightLimit = number(input, "weightLimit");
            validateWeightLimit(weightLimit);
            Trip trip = tripService.create(user.getId(), name, destination,
                    string(input, "startDate"), integer(input, "days"), number(input, "budget"));
            weightLimitService.saveForTrip(trip.getId(), user.getId(), weightLimit);
            sendJson(exchange, 201, tripJson(trip));
            return;
        }
        sendError(exchange, 405, "Use GET or POST for trips.");
    }

    private void handleTrip(HttpExchange exchange, String method, User user, String tripId) throws IOException {
        requireId(tripId);
        if (method.equals("PUT")) {
            Map<String, Object> input = readObject(exchange);
            String destination = string(input, "destination");
            String name = optionalString(input, "name", destination);
            Double weightLimit = optionalNumber(input, "weightLimit");
            if (weightLimit != null) validateWeightLimit(weightLimit);
            Trip trip = tripService.update(tripId, user.getId(), name, destination,
                    string(input, "startDate"), integer(input, "days"), number(input, "budget"));
            if (weightLimit != null) weightLimitService.saveForTrip(tripId, user.getId(), weightLimit);
            sendJson(exchange, 200, tripJson(trip));
        } else if (method.equals("DELETE")) {
            tripService.delete(tripId, user.getId());
            sendJson(exchange, 200, Map.of("message", "Trip deleted."));
        } else {
            sendError(exchange, 405, "Use PUT or DELETE for a trip.");
        }
    }

    private void handleItems(HttpExchange exchange, String method, User user) throws IOException {
        String tripId = queryParameter(exchange, "tripId");
        requireId(tripId);
        if (method.equals("GET")) {
            String search = queryParameter(exchange, "search");
            sendJson(exchange, 200, itemService.listForTrip(tripId, user.getId(), search)
                    .stream().map(this::itemJson).toList());
            return;
        }
        if (method.equals("POST")) {
            PackingItem item = readItem(readObject(exchange), null, user);
            sendJson(exchange, 201, itemJson(item));
            return;
        }
        sendError(exchange, 405, "Use GET or POST for packing items.");
    }

    private void handleItem(HttpExchange exchange, String method, User user, String itemId) throws IOException {
        requireId(itemId);
        Map<String, Object> input = readObject(exchange);
        String tripId = string(input, "tripId");
        requireId(tripId);
        if (method.equals("PUT")) {
            sendJson(exchange, 200, itemJson(readItem(input, itemId, user)));
        } else if (method.equals("DELETE")) {
            itemService.delete(itemId, user.getId(), tripId);
            sendJson(exchange, 200, Map.of("message", "Packing item deleted."));
        } else {
            sendError(exchange, 405, "Use PUT or DELETE for a packing item.");
        }
    }

    private PackingItem readItem(Map<String, Object> input, String itemId, User user) throws IOException {
        String tripId = string(input, "tripId");
        String name = string(input, "name");
        String category = string(input, "category");
        int quantity = integer(input, "quantity");
        double weight = number(input, "weight");
        double price = number(input, "price");
        boolean packed = bool(input, "packed");
        boolean purchased = bool(input, "purchased");
        if (itemId == null) {
            return itemService.create(user.getId(), tripId, name, category, quantity, weight, price,
                    packed, purchased);
        }
        return itemService.update(itemId, user.getId(), tripId, name, category, quantity, weight, price,
                packed, purchased);
    }

    private Map<String, Object> sessionResponse(User user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user.getId());
        return Map.of("token", token, "user", userJson(user));
    }

    private Map<String, Object> userJson(User user) {
        return Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail());
    }

    private User authenticatedUser(HttpExchange exchange) throws IOException {
        String token = bearerToken(exchange);
        String userId = token == null ? null : sessions.get(token);
        if (userId == null) throw new UnauthorizedException();
        for (User user : storage.readUsers()) {
            if (user.getId().equals(userId)) return user;
        }
        sessions.remove(token);
        throw new UnauthorizedException();
    }

    private String bearerToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        return authorization.substring("Bearer ".length()).trim();
    }

    private void serveFrontend(HttpExchange exchange, String requestPath) throws IOException {
        String relative = requestPath.equals("/") ? "index.html" : requestPath.substring(1);
        Path requestedFile = frontendDirectory.resolve(relative).normalize();
        if (!requestedFile.startsWith(frontendDirectory) || !Files.isRegularFile(requestedFile)) {
            sendError(exchange, 404, "Page not found.");
            return;
        }
        String contentType = contentType(requestedFile);
        byte[] content = Files.readAllBytes(requestedFile);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content);
    }

    private String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        return "application/octet-stream";
    }

    private Map<String, Object> tripJson(Trip trip) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", trip.getId());
        result.put("name", trip.getName());
        result.put("destination", trip.getDestination());
        result.put("startDate", trip.getStartDate());
        result.put("days", trip.getDays());
        result.put("budget", trip.getBudget());
        return result;
    }

    private Map<String, Object> itemJson(PackingItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId());
        result.put("tripId", item.getTripId());
        result.put("name", item.getName());
        result.put("category", item.getCategory());
        result.put("quantity", item.getQuantity());
        result.put("weight", item.getWeight());
        result.put("price", item.getPrice());
        result.put("packed", item.isPacked());
        result.put("purchased", item.isPurchased());
        result.put("totalWeight", item.getTotalWeight());
        result.put("totalPrice", item.getTotalPrice());
        return result;
    }

    private Map<String, Object> readObject(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Request body is too large.");
        Object parsed = Json.parse(new String(bytes, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected a JSON object.");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
        }
        return result;
    }

    private String string(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (!(value instanceof String text)) throw new IllegalArgumentException("Missing or invalid " + key + ".");
        return text;
    }

    private double number(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Missing or invalid " + key + ".");
        }
        return number.doubleValue();
    }

    private int integer(Map<String, Object> input, String key) {
        double value = number(input, key);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Missing or invalid " + key + ".");
        }
        return (int) value;
    }

    private boolean bool(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException("Missing or invalid " + key + ".");
        return result;
    }

    private String queryParameter(HttpExchange exchange, String parameter) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return "";
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (name.equals(parameter)) {
                return pair.length == 1 ? "" : URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private void requireId(String id) {
        if (id == null || id.isBlank() || id.contains("/") || id.contains("\\")) {
            throw new IllegalArgumentException("A valid ID is required.");
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = Json.stringify(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        String safeMessage = message == null || message.isBlank() ? "The request could not be completed." : message;
        sendJson(exchange, status, Map.of("error", safeMessage));
    }

    private static final class UnauthorizedException extends IOException {
        private static final long serialVersionUID = 1L;

        private UnauthorizedException() {
            super("Please sign in again.");
        }
    }
}
