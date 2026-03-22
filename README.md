# Tap & Go — Transit Coding Exercise

## Requirements
- Java 21
- Docker

## Quick Start
Use the following URL to download the project
- https://github.com/gayankaveenda/tap-service.git

Once you download the project, you should automatically see the gradle loading up the project for you.
- If your java version is Java 21, then this should work seemlessly. 
- if it java version 24, i have provided comments in the gradle file
- If it is higher you need to update the build.gradle file java version to say 24 and also update jacoco version to 0.8.13

## Running the project
```bash
# 1. Start infrastructure
-- CD into the project root folder.
-- will run docker compose for postgreSQL and also initialize the schema required.
-- will build and run all tests. Also runs the fare data to initialize the table schema and data.

./start.sh
./gradlew build

# 2. Run the application
./gradlew bootRun

# 3. Upload tap events CSV
curl -X POST http://localhost:8080/api/api/v1/ingest \
  -F "file=@taps.csv"

# 4. Download trips output
curl http://localhost:8080/api/api/v1/trips/export -o trips.csv

```

## Test Files
Swagger UI: http://localhost:8080/api/swagger-ui.html
Health check: http://localhost:8080/api/actuator/health

Test files are located under src/test/resources/

- For overall scenarios please run tap_all_scenarios.csv
- I have provided some user scenario csv files for load testing as well.

## How It Works

### Processing Pipeline

```
POST /api/v1/ingest
        │
        
TapRecordParserService      streams CSV rows, skips header (Large loading possible, no memory leaks or spikes)
        │
        
TapRecordMapper             validates each row
                            hashes PAN  → panHash (matching key)
                            masks PAN   → ****XXXX (display only)
                            raw PAN discarded — never stored
        │
        
IngestionService            saves valid rows to tap_events (status=PENDING)
                            saves invalid rows to failed_ingestion_records
                            batch inserts for performance
        │
        
TripProcessingJob           @Scheduled — runs every 5 seconds
        │                   picks up PENDING tap_events in batches
        
TripOrchestrator            saga orchestrator — explicit steps
    step 1: match TAP OFF to PENDING TAP ON (by panHash + busId)
    step 2: TripStateService resolves COMPLETED / CANCELLED /INCOMPLETE
    step 3: save Trip with all output columns pre-computed
        │
OrphanedProcessingJob       @Scheduled — runs every 2 minutes
        │                   finds TAP ONs older than cutoff with no TAP OFF
                            (For demo project wait time is 3 minutes)
        
TripStateService            resolves as INCOMPLETE, charges max fare
        │
        
GET /api/v1/trips/export
        │
TripOutputWriterService     reads trips table, writes CSV (Filter by state, start and endtime)
                            no business logic at read time
```
---

## Trip Status Rules
 - All stated rules in the assignment.
 - In addition, I believe just because a TAP ON is without a TAP OFF meaning the rest the file is not yet arrived. Hence immediately processing the record is discouraged.
 - Hence, I wait for few minutes before considering it an ORPHANED record.
 - Negative durations are handled.
 
 ```
 TAP ON + TAP OFF at different stops - `COMPLETED` - Route fare
 TAP ON + TAP OFF at same stop - `CANCELLED` - $0.00 (NOT In the trips table, these are in the tapevents table)
 TAP ON with no TAP OFF within cutoff - `INCOMPLETE` - Max fare from origin
 
 Duplicate TAP ON (same card, same bus) - Older → `CANCELED_DUPLICATE` AND Newer ON used for matching
 Duplicate TAP OFF | First OFF matched and second `UNMATCHED`
 TAP OFF timestamp before TAP ON Hence `INVALID` Not charged — data error
 TAP ON on Bus A, TAP OFF on Bus B Hence AP ON on Bus A, TAP OFF on Bus B Therefore Max fare for Bus A trip
```
---

## PAN Security

Raw PAN numbers are never stored in the database. On ingestion two values are derived and the raw PAN is immediately discarded:

- **panHash** — `SHA-256(pan + salt)` — used as the matching key to pair TAP ON with TAP OFF.
- **maskedPan** — `****5559` — stored for display in output CSV and API responses.

---

## Assumptions

1. **Input file is well-formed per the assignment spec.** Rows with missing fields, invalid dates, or non-numeric PANs are captured in `failed_ingestion_records` and do not stop processing of subsequent rows.

2. **TAP ON is matched to TAP OFF by `panHash + busId`.** A passenger on two different buses simultaneously is treated as two independent journeys. This prevents cross-bus false matches.

3. **Most recent PENDING TAP ON is used when multiple exist.** If a passenger taps ON twice on the same bus without tapping OFF, the older TAP ON is marked `CANCELED_DUPLICATE` and the newer one is matched to the TAP OFF. The older trip is resolved as INCOMPLETE.

4. **TAP OFF with no preceding TAP ON is discarded.** No trip is created and no charge is applied. The event is saved as `UNMATCHED` for audit purposes.

5. **Events are matched by `tappedAt` timestamp, not ingestion order.** A TAP OFF arriving in a later file will correctly match a TAP ON from an earlier file as long as the timestamps are consistent.

6. **INCOMPLETE resolution runs on a schedule.** TAP ONs that have not been matched within the configured cutoff period are resolved as INCOMPLETE by `OrphanedProcessingJob`.
7. The cutoff is configurable via `app.processing.scheduler.orphan-cutoff-minutes`.

8. **Fare rules are bidirectional.** Stop1→Stop2 and Stop2→Stop1 cost the same. Both directions are stored as explicit rows in `fare_rules` — no directional logic exists in application code.

9. **Fare rules are cached in memory.** Loaded from the database at startup into a Caffeine cache. Cache expires hourly. Any fare changes take effect within one hour without a restart.

10. **Currency is AUD.** All amounts use `BigDecimal` with 2 decimal places to avoid floating point rounding errors.

11. **Idempotency.** Re-uploading the same CSV file does not create duplicate tap events. Each row is checked against `originalId + tappedAt + panHash` before saving.

---

## Running Tests

```bash
# All tests (requires Docker for Testcontainers)
./gradlew test

# Coverage report
open build/jacocoHtml/index.html
```

### Test coverage
I used git copilot assistance to generate tests as modern day test class generation is mostly done using ai tools.
However, I have fixed bugs, updated test cases post generations (You can see my commit history on various bug fixes, and alterations)

- `PanTokeniserTest` — hash determinism, masking, edge cases
- `CommonUtilsTest` — normalize and PAN validation
- `TapRecordMapperTest` — field mapping, PAN security, validation errors
- `FareCalculatorServiceTest` — all 6 bidirectional fares, max fares, cache
- `TripStateServiceTest` — all state transitions, all fare combinations
- `TripOutputWriterServiceTest` — exact CSV output format verification
- `JobLockTest` — concurrent lock behaviour
- `TripOrchestratorTest` — all processing scenarios including edge cases

## Design Decisions

**Transactional Outbox Pattern** 
tap events are saved to the database with `status=PENDING` before any processing occurs. 
The scheduler picks them up asynchronously. 
If the application crashes mid-processing, events remain `PENDING` and are retried on the next run. 
If the record cannot be read as a tapEvent, then it will go to a table with failed_ingestion_record.
 - The raw input file is saved as is with failure reasons.
No message broker required.

## NOTE: 
You can load 10K or 100K records but the memory footprint will remain constant. 
I have used batch size to sequentially read the file and not load everything into the memory.
Also, I have saved in batches, reducing hibernate N+1 problem
I never save PAN as is and use encryption and used the hashed version. also for displaying i only use masked_PAN with last 4 digits.
Original pan is never shared or saved anywhere and immediately discarded when hashPan is generated.


### ARCHITECHTURE DECISIONS

**Saga Orchestration** — 
`TripOrchestrator` coordinates each processing step explicitly in sequence. 
If any step fails, the tap event is marked as FAILED.
FAILED transactions can be viewed in tap events.
These can be updated to PENDING and be reprocessed anytime for later retries.

**Pre-computed Output Columns** 
all trip output fields (charge amount, duration, masked PAN etc.) are calculated once during processing and stored in the `trips` table. 
The query API reads directly with no business logic at read time.
No sensitive data is in this table.

**Bidirectional Fare Storage** 
both directions of each route are stored as explicit rows. (cross-product). This makes it easy to find maxFare too. 
Fare lookup is always a single query.
That too loaded into cache, and refreshed every 1hr to save db calls

**What I Would Add With More Time** 
OAuth2 authentication on the ingest endpoint, and read end points
API gateway manager for multiple file handling
message broker instead of cron so the solution will be truly distributed.

OPTIONAL - Ingestion, Core Logic, Display will be seperate microservices if you like distributed architectures.
