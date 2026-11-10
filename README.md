# BAGORITHM - Smart Packing Recommendation System

A beginner-friendly packing planner built with Core Java, the built-in `HttpServer`, and HTML, CSS, and JavaScript. It uses no database, external libraries, or build framework. The server saves accounts, trips, and packing items as readable text files in the `data` folder.

## Requirements

- Java 17 or newer (the project has been tested with Java 25)
- A modern web browser

## Compile and run on Windows PowerShell

Run these commands from the project folder:

```powershell
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java | ForEach-Object FullName)
java -cp out balgorithm.server.BalgorithmServer
```

Open [http://localhost:8080](http://localhost:8080). Stop the server with `Ctrl+C`. Missing text files are created on the first run; existing files are preserved.

The server listens on `0.0.0.0` and uses the `PORT` environment variable when set, defaulting to port 8080 for local use. To choose another available port in PowerShell, set `PORT` before starting Java, for example:

```powershell
$env:PORT = "8081"
java -cp out balgorithm.server.BalgorithmServer
```

Text files are saved in the project `data` directory by default. Set `BAGORITHM_DATA_DIR` to use another directory (for example, a mounted persistent disk on a hosting provider). Relative paths are resolved from the project directory. `BAGORITHM_FRONTEND_DIR` can similarly override the `frontend` directory if the application is started from a different deployment layout. On hosts with ephemeral filesystems, configure a persistent disk for `BAGORITHM_DATA_DIR` to retain accounts and trip data across restarts or redeployments.

## Using the application

1. Register an account, then sign in. Accounts are stored locally; passwords are salted and hashed before saving.
2. Create a trip with its destination, dates, duration, and budget.
3. Choose that trip in the header. In Smart Picks, enter a destination and manually select weather and activity; BAGORITHM does not fetch a forecast. Generate priority-ranked outfit suggestions for destinations including Kerala, Goa, Ooty, Manali, Delhi, Mumbai, Chennai, and Bengaluru. Sort by source/category or weight in either direction. Add suggested items to the trip packing list, or enter your own in the packing checklist or shopping list.
4. Search, edit, remove, and mark items packed or purchased. The checklist, progress, budget, and weight summaries update together. Enter a weight and price when adding an item; purchased shopping items (including quantity) update the spent and remaining budget immediately.
5. On Overview, Smart Packing Score grades packing checklist completion and packed bag weight against the trip's luggage limit. Update that limit in grams in the score card; the setting is saved separately for each trip.
6. Open Day-wise outfits to choose a destination, weather, trip length, and activity for every day. Select one or more clothing items saved to the packing checklist, generate or regenerate a plan, then save it to view it again later. Plans include the selected items and keep their storage separate from Previous Trips.
7. Trips, packing lists, luggage limits, and saved outfit plans remain available after the server restarts.

## Project layout

```text
frontend/
  index.html
  CSS/style.css
  JS/app.js
src/balgorithm/
  model/       User, Trip, PackingItem, outfit-plan, and luggage-limit data classes
  service/     Account, trip, item, recommendation, outfit, and luggage-limit rules
  storage/     Plain-text file reading and writing
  server/      HTTP routing, JSON handling, and application entry point
data/          Text files for accounts, trips, packing items, outfit plans, and luggage limits
out/           Compiled Java classes
```

## Java class and method guide

- **`User`** holds an account ID, display name, email, and salted password hash. Its `toFileLine` and `fromFileLine` methods convert account data to and from a text-file row.
- **`Trip`** holds the owner, destination, start date, duration, and budget. Its `toFileLine` and `fromFileLine` methods save and restore those details.
- **`PackingItem`** represents a packing, clothing, or shopping-list entry. `getTotalWeight` and `getTotalPrice` multiply the per-item values by the quantity; packed and purchased flags drive the checklists.
- **`FileStorage`** creates the data folder. `readUsers`, `readTrips`, and `readItems` load text-file rows into objects; the matching `write...` methods save them. `writeRows` writes a temporary file before replacing the old one to reduce the chance of a partial save.
- **`AuthService`** `register` checks account details and saves the new user; `login` checks the email and password. `hashPassword` uses Java's PBKDF2 password-hashing tool with a random salt rather than saving the original password.
- **`TripService`** `listForUser` and `findForUser` only return trips owned by the signed-in user. `create`, `update`, and `delete` validate and save trip changes; deleting a trip also removes its packing items.
- **`ItemService`** `listForTrip` loads or searches one trip's items. `create`, `update`, and `delete` validate changes and check that the user owns the trip before saving.
- **`RecommendationService`** `recommend` normalizes a destination and validates the manually selected weather, activity, and trip length. `addWeatherSuggestions`, `addActivitySuggestions`, and `addDestinationSuggestions` create deduplicated outfit ideas with priority, source, and weight details.
- **`OutfitPlan`** stores a trip's destination, weather, and day-by-day activity/outfit details in `outfit-plans.txt`. Each day includes four generated clothing pieces and the clothing items selected from the packing checklist.
- **`OutfitPlanService`** validates planner input and selected clothing items, creates practical outfits from destination/weather/activity rules, and reads or saves plans separately for the signed-in trip.
- **`TripWeightLimit`** and **`WeightLimitService`** store and retrieve the user's per-trip luggage limit in `weight-limits.txt`; existing trip and packing-item files keep their original format.
- **`BalgorithmServer`** `main` starts Java's built-in HTTP server. `handleApi` routes browser requests to the services, and `serveFrontend` sends the HTML, CSS, and JavaScript files to the browser.
- **`Json`** `parse` reads browser JSON into Java objects, while `stringify` turns Java objects into JSON responses. It is a small, dependency-free reader/writer for the values this application uses.

## Smart Packing Score

The score is calculated from the selected trip's saved checklist, excluding shopping-list items:

- Up to 80 points come from the proportion of checklist items marked packed.
- 20 points are awarded when the weight of packed items is at or below the saved luggage limit; otherwise, 0 points are awarded for weight.

The two parts are added and rounded to a whole-number score. A helpful empty state is shown when the trip has no packing items.

## Outfit planner storage and API

The planner generates outfits on the server from the selected destination, weather, each day's activity, and the trip's selected clothing items. Save stores a separate row in `data\outfit-plans.txt`; weight preferences use `data\weight-limits.txt`. Both files are created if missing and do not replace users, trips, or packing lists. The matching authenticated API routes are `POST /api/outfit-plans/generate`, `GET/POST /api/outfit-plans`, and `GET/PUT /api/packing-limit`.

## Data and privacy

This is a local college-project demo, not a production hosting setup. Account passwords are salted and hashed, but there are no email confirmations, password recovery, or encrypted data files. Anyone with access to this computer can read the trip and item text files. Keep the server on a trusted machine.
