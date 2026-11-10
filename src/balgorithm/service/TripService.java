package balgorithm.service;

import balgorithm.model.PackingItem;
import balgorithm.model.Trip;
import balgorithm.storage.FileStorage;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

public class TripService {
    private final FileStorage storage;

    public TripService(FileStorage storage) {
        this.storage = storage;
    }

    public List<Trip> listForUser(String userId) throws IOException {
        return storage.readTrips().stream().filter(trip -> trip.getUserId().equals(userId)).toList();
    }

    public Trip findForUser(String tripId, String userId) throws IOException {
        return listForUser(userId).stream().filter(trip -> trip.getId().equals(tripId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trip not found."));
    }

    public Trip create(String userId, String destination, String date, int days, double budget) throws IOException {
        return create(userId, destination, destination, date, days, budget);
    }

    public Trip create(String userId, String name, String destination, String date, int days,
                       double budget) throws IOException {
        Trip trip = validatedTrip(UUID.randomUUID().toString(), userId, name, destination, date, days, budget);
        List<Trip> trips = storage.readTrips();
        trips.add(trip);
        storage.writeTrips(trips);
        return trip;
    }

    public Trip update(String tripId, String userId, String destination, String date,
                       int days, double budget) throws IOException {
        return update(tripId, userId, destination, destination, date, days, budget);
    }

    public Trip update(String tripId, String userId, String name, String destination, String date,
                       int days, double budget) throws IOException {
        Trip existing = findForUser(tripId, userId);
        Trip updated = validatedTrip(existing.getId(), userId, name, destination, date, days, budget);
        List<Trip> trips = storage.readTrips();
        trips.replaceAll(trip -> trip.getId().equals(tripId) ? updated : trip);
        storage.writeTrips(trips);
        return updated;
    }

    public void delete(String tripId, String userId) throws IOException {
        findForUser(tripId, userId);
        storage.writeTrips(storage.readTrips().stream().filter(trip -> !trip.getId().equals(tripId)).toList());
        List<PackingItem> items = storage.readItems();
        storage.writeItems(items.stream().filter(item -> !item.getTripId().equals(tripId)).toList());
    }

    private Trip validatedTrip(String id, String userId, String name, String destination, String date, int days,
                               double budget) {
        String cleanName = name == null ? "" : name.trim();
        String cleanDestination = destination == null ? "" : destination.trim();
        if (cleanName.isBlank() || cleanName.length() > 100) {
            throw new IllegalArgumentException("Enter a trip name between 1 and 100 characters.");
        }
        if (cleanDestination.isBlank() || cleanDestination.length() > 100) {
            throw new IllegalArgumentException("Enter a destination between 1 and 100 characters.");
        }
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("Choose a valid trip start date.");
        }
        if (days < 1 || days > 365) throw new IllegalArgumentException("Trip length must be 1–365 days.");
        if (!Double.isFinite(budget) || budget < 0 || budget > 1_000_000_000) {
            throw new IllegalArgumentException("Budget must be a non-negative amount.");
        }
        return new Trip(id, userId, cleanName, cleanDestination, date, days, budget);
    }
}
