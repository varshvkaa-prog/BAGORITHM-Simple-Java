package balgorithm.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Trip {
    private final String id;
    private final String userId;
    private final String name;
    private final String destination;
    private final String startDate;
    private final int days;
    private final double budget;

    public Trip(String id, String userId, String destination, String startDate, int days, double budget) {
        this(id, userId, destination, destination, startDate, days, budget);
    }

    public Trip(String id, String userId, String name, String destination, String startDate, int days,
                double budget) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.destination = destination;
        this.startDate = startDate;
        this.days = days;
        this.budget = budget;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getDestination() { return destination; }
    public String getStartDate() { return startDate; }
    public int getDays() { return days; }
    public double getBudget() { return budget; }

    public String toFileLine() {
        return encode(id) + "\t" + encode(userId) + "\t" + encode(destination) + "\t"
                + encode(startDate) + "\t" + encode(Integer.toString(days)) + "\t"
                + encode(Double.toString(budget)) + "\t" + encode(name);
    }

    public static Trip fromFileLine(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 6 && fields.length != 7) throw new IllegalArgumentException("Invalid trip row");
        String destination = decode(fields[2]);
        String name = fields.length == 7 ? decode(fields[6]) : destination;
        return new Trip(decode(fields[0]), decode(fields[1]), name, destination, decode(fields[3]),
                Integer.parseInt(decode(fields[4])), Double.parseDouble(decode(fields[5])));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
