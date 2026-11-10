package balgorithm.storage;

import balgorithm.model.PackingItem;
import balgorithm.model.OutfitPlan;
import balgorithm.model.Trip;
import balgorithm.model.TripWeightLimit;
import balgorithm.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class FileStorage {
    private final Path dataDirectory;
    private final Path usersFile;
    private final Path tripsFile;
    private final Path itemsFile;
    private final Path outfitPlansFile;
    private final Path weightLimitsFile;

    public FileStorage(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        Files.createDirectories(this.dataDirectory);
        usersFile = this.dataDirectory.resolve("users.txt");
        tripsFile = this.dataDirectory.resolve("trips.txt");
        itemsFile = this.dataDirectory.resolve("items.txt");
        outfitPlansFile = this.dataDirectory.resolve("outfit-plans.txt");
        weightLimitsFile = this.dataDirectory.resolve("weight-limits.txt");
        createIfMissing(usersFile);
        createIfMissing(tripsFile);
        createIfMissing(itemsFile);
        createIfMissing(outfitPlansFile);
        createIfMissing(weightLimitsFile);
    }

    public synchronized List<User> readUsers() throws IOException {
        return readRows(usersFile, User::fromFileLine);
    }

    public synchronized List<Trip> readTrips() throws IOException {
        return readRows(tripsFile, Trip::fromFileLine);
    }

    public synchronized List<PackingItem> readItems() throws IOException {
        return readRows(itemsFile, PackingItem::fromFileLine);
    }

    public synchronized List<OutfitPlan> readOutfitPlans() throws IOException {
        return readRows(outfitPlansFile, OutfitPlan::fromFileLine);
    }

    public synchronized List<TripWeightLimit> readWeightLimits() throws IOException {
        return readRows(weightLimitsFile, TripWeightLimit::fromFileLine);
    }

    public synchronized void writeUsers(List<User> users) throws IOException {
        writeRows(usersFile, users.stream().map(User::toFileLine).toList());
    }

    public synchronized void writeTrips(List<Trip> trips) throws IOException {
        writeRows(tripsFile, trips.stream().map(Trip::toFileLine).toList());
    }

    public synchronized void writeItems(List<PackingItem> items) throws IOException {
        writeRows(itemsFile, items.stream().map(PackingItem::toFileLine).toList());
    }

    public synchronized void writeOutfitPlans(List<OutfitPlan> plans) throws IOException {
        writeRows(outfitPlansFile, plans.stream().map(OutfitPlan::toFileLine).toList());
    }

    public synchronized void writeWeightLimits(List<TripWeightLimit> limits) throws IOException {
        writeRows(weightLimitsFile, limits.stream().map(TripWeightLimit::toFileLine).toList());
    }

    private <T> List<T> readRows(Path file, Function<String, T> parser) throws IOException {
        List<T> rows = new ArrayList<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.isBlank()) continue;
            try {
                rows.add(parser.apply(line));
            } catch (RuntimeException exception) {
                throw new IOException("Invalid data in " + file.getFileName() + " at line " + lineNumber, exception);
            }
        }
        return rows;
    }

    private void writeRows(Path file, List<String> rows) throws IOException {
        Path temporaryFile = Files.createTempFile(dataDirectory, file.getFileName().toString(), ".tmp");
        try {
            Files.write(temporaryFile, rows, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void createIfMissing(Path file) throws IOException {
        if (Files.notExists(file)) Files.createFile(file);
    }
}
