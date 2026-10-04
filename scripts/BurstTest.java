import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BurstTest {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    private static final ExecutorService executor = Executors.newFixedThreadPool(64);
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(20))
            .executor(executor)
            .build();

    public static void main(String[] args) throws Exception {
        String inputUrl = (args.length > 0) ? args[0].replaceAll("/+$", "") : "http://localhost:8080";
        if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://")) {
            inputUrl = "https://" + inputUrl;
        }
        final String targetBaseUrl = inputUrl;

        System.out.println("\n" + BOLD + CYAN + "======================================================================" + RESET);
        System.out.println(BOLD + CYAN + "   Paytm Money Seat Reservation at Scale — Live Concurrency Burst     " + RESET);
        System.out.println(BOLD + CYAN + "======================================================================" + RESET + "\n");
        System.out.println("Target Base URL: " + BOLD + targetBaseUrl + RESET);

        // Pre-flight health probe
        HttpRequest healthReq = HttpRequest.newBuilder()
                .uri(URI.create(targetBaseUrl + "/actuator/health"))
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> healthResp = client.send(healthReq, HttpResponse.BodyHandlers.ofString());
        if (healthResp.statusCode() == 200) {
            System.out.println(GREEN + "✓ Health probe UP (HTTP 200): " + healthResp.body() + RESET);
        } else {
            System.out.println(RED + "Warning: /actuator/health returned HTTP " + healthResp.statusCode() + RESET);
        }

        // Setup: Admin Token & Create 100-Seat Show
        System.out.println("\n[Setup] Creating 100-Seat Show...");
        List<String> seats = new ArrayList<>();
        for (String row : List.of("A", "B", "C", "D")) {
            for (int i = 1; i <= 25; i++) {
                seats.add(row + i);
            }
        }

        String createShowJson = "{\"name\":\"Burst On-Sale Arena - " + UUID.randomUUID().toString().substring(0, 6) +
                "\",\"price_paise\":25000,\"seats\":[" +
                String.join(",", seats.stream().map(s -> "\"" + s + "\"").toList()) + "]}";

        HttpRequest createShowReq = HttpRequest.newBuilder()
                .uri(URI.create(targetBaseUrl + "/shows"))
                .header("Authorization", "Bearer admin")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createShowJson))
                .build();
        HttpResponse<String> showResp = client.send(createShowReq, HttpResponse.BodyHandlers.ofString());
        if (showResp.statusCode() != 201) {
            System.err.println(RED + "Failed to create show: HTTP " + showResp.statusCode() + " - " + showResp.body() + RESET);
            System.exit(1);
        }

        String showJson = showResp.body();
        final String targetShowId = extractJsonValue(showJson, "id");
        int totalSeats = Integer.parseInt(extractJsonValue(showJson, "total_seats", "totalSeats"));
        System.out.println(GREEN + "✓ Created Show ID: " + targetShowId + " with " + totalSeats + " seats" + RESET);

        // -------------------------------------------------------------
        // Phase 1: Hot Seat Storm (500 Concurrent Threads on Seat A12)
        // -------------------------------------------------------------
        System.out.println("\n" + BOLD + "[Phase 1] Hot Seat Storm: 500 concurrent requests competing for single seat 'A12'..." + RESET);
        long p1Start = System.currentTimeMillis();
        List<CompletableFuture<Integer>> p1Futures = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            final String u = "hot_user_" + i;
            p1Futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String reqBody = "{\"seats\":[\"A12\"],\"idempotency_key\":\"" + UUID.randomUUID() + "\"}";
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(targetBaseUrl + "/shows/" + targetShowId + "/reserve"))
                            .header("Authorization", "Bearer " + u)
                            .header("Content-Type", "application/json")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                            .build();
                    return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                } catch (Exception e) {
                    return 599;
                }
            }, executor));
        }

        CompletableFuture.allOf(p1Futures.toArray(new CompletableFuture[0])).join();
        long p1Duration = System.currentTimeMillis() - p1Start;

        int p1_201 = 0, p1_409 = 0, p1_5xx = 0;
        for (CompletableFuture<Integer> f : p1Futures) {
            int code = f.get();
            if (code == 201) p1_201++;
            else if (code == 409) p1_409++;
            else if (code >= 500) p1_5xx++;
        }
        boolean p1Pass = (p1_201 == 1) && (p1_409 == 499) && (p1_5xx == 0);
        System.out.println("  -> Completed in " + p1Duration + "ms: " + p1_201 + " confirmed (201), " + p1_409 + " declined (409), " + p1_5xx + " errors (5xx)");

        // -------------------------------------------------------------
        // Phase 2: Per-User Limit Storm (1 User, 10 Parallel Requests)
        // -------------------------------------------------------------
        System.out.println("\n" + BOLD + "[Phase 2] Per-User Limit Storm: 1 user sends 10 parallel requests (limit = 4)..." + RESET);
        String greedyToken = "greedy_user_burst";
        List<CompletableFuture<Integer>> p2Futures = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            final String seat = "B" + i;
            p2Futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String reqBody = "{\"seats\":[\"" + seat + "\"],\"idempotency_key\":\"" + UUID.randomUUID() + "\"}";
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(targetBaseUrl + "/shows/" + targetShowId + "/reserve"))
                            .header("Authorization", "Bearer " + greedyToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                            .build();
                    return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                } catch (Exception e) {
                    return 599;
                }
            }, executor));
        }

        CompletableFuture.allOf(p2Futures.toArray(new CompletableFuture[0])).join();
        int p2_201 = 0, p2_409 = 0, p2_5xx = 0;
        for (CompletableFuture<Integer> f : p2Futures) {
            int code = f.get();
            if (code == 201) p2_201++;
            else if (code == 409) p2_409++;
            else if (code >= 500) p2_5xx++;
        }
        boolean p2Pass = (p2_201 == 4) && (p2_409 == 6) && (p2_5xx == 0);
        System.out.println("  -> Completed: " + p2_201 + " confirmed (201), " + p2_409 + " declined (409), " + p2_5xx + " errors (5xx)");

        // -------------------------------------------------------------
        // Phase 3: Idempotent Retries (50 Concurrent Requests, Same Key)
        // -------------------------------------------------------------
        System.out.println("\n" + BOLD + "[Phase 3] Idempotent Retries: 50 concurrent requests with IDENTICAL key on 'C1'..." + RESET);
        String idemToken = "idem_user_burst";
        String sharedKey = "idem-key-" + UUID.randomUUID();
        List<CompletableFuture<Integer>> p3Futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            p3Futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String reqBody = "{\"seats\":[\"C1\"],\"idempotency_key\":\"" + sharedKey + "\"}";
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(targetBaseUrl + "/shows/" + targetShowId + "/reserve"))
                            .header("Authorization", "Bearer " + idemToken)
                            .header("Content-Type", "application/json")
                            .header("Idempotency-Key", sharedKey)
                            .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                            .build();
                    return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                } catch (Exception e) {
                    return 599;
                }
            }, executor));
        }

        CompletableFuture.allOf(p3Futures.toArray(new CompletableFuture[0])).join();
        int p3_201 = 0, p3_200 = 0, p3_5xx = 0;
        for (CompletableFuture<Integer> f : p3Futures) {
            int code = f.get();
            if (code == 201) p3_201++;
            else if (code == 200) p3_200++;
            else if (code >= 500) p3_5xx++;
        }
        boolean p3Pass = (p3_201 + p3_200 == 50) && (p3_201 >= 1) && (p3_5xx == 0);
        System.out.println("  -> Completed: " + p3_201 + " new (201), " + p3_200 + " replayed (200), " + p3_5xx + " errors (5xx)");

        // -------------------------------------------------------------
        // Phase 4: Idempotency Key Collision (Same Key, Different Seat)
        // -------------------------------------------------------------
        System.out.println("\n" + BOLD + "[Phase 4] Key Collision Check: Reusing key with altered payload (Seat 'C2')..." + RESET);
        String p4Body = "{\"seats\":[\"C2\"],\"idempotency_key\":\"" + sharedKey + "\"}";
        HttpRequest p4Req = HttpRequest.newBuilder()
                .uri(URI.create(targetBaseUrl + "/shows/" + targetShowId + "/reserve"))
                .header("Authorization", "Bearer " + idemToken)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", sharedKey)
                .POST(HttpRequest.BodyPublishers.ofString(p4Body))
                .build();
        int p4Status = client.send(p4Req, HttpResponse.BodyHandlers.discarding()).statusCode();
        boolean p4Pass = (p4Status == 409);
        System.out.println("  -> Status returned: " + p4Status + " (Expected 409 Conflict)");

        // -------------------------------------------------------------
        // Phase 5: Reconciliation Verification via GET /shows/{id}
        // -------------------------------------------------------------
        System.out.println("\n" + BOLD + "[Phase 5] Reconciliation Query: Verifying seat integrity invariant via GET /shows/{id}..." + RESET);
        HttpRequest showStateReq = HttpRequest.newBuilder()
                .uri(URI.create(targetBaseUrl + "/shows/" + targetShowId))
                .GET().build();
        HttpResponse<String> showStateResp = client.send(showStateReq, HttpResponse.BodyHandlers.ofString());
        String stateJson = showStateResp.body();

        int available = Integer.parseInt(extractNestedJsonValue(stateJson, "counts", "available"));
        int held = Integer.parseInt(extractNestedJsonValue(stateJson, "counts", "held"));
        int confirmed = Integer.parseInt(extractNestedJsonValue(stateJson, "counts", "confirmed"));
        int sumTotal = available + held + confirmed;

        int expectedConfirmed = 1 + 4 + 1; // 1 (A12) + 4 (B1..B4) + 1 (C1)
        boolean reconPass = (sumTotal == totalSeats) && (confirmed == expectedConfirmed);

        // -------------------------------------------------------------
        // Outcome Summary Table
        // -------------------------------------------------------------
        System.out.println("\n" + BOLD + CYAN + "======================================================================" + RESET);
        System.out.println(BOLD + CYAN + "                    BURST RECONCILIATION REPORT                       " + RESET);
        System.out.println(BOLD + CYAN + "======================================================================" + RESET);
        System.out.printf("%-38s | %-18s | %s\n", "PHASE", "OUTCOME", "STATUS");
        System.out.println("----------------------------------------------------------------------");

        printPhaseRow("Phase 1: Hot Seat (500 reqs)", p1_201 + "x201, " + p1_409 + "x409", p1Pass);
        printPhaseRow("Phase 2: User Limit (10 reqs)", p2_201 + "x201, " + p2_409 + "x409", p2Pass);
        printPhaseRow("Phase 3: Idempotent Retries (50 reqs)", p3_201 + "x201, " + p3_200 + "x200", p3Pass);
        printPhaseRow("Phase 4: Key Collision (1 req)", "Status: " + p4Status, p4Pass);
        printPhaseRow("Phase 5: Reconciliation Invariant", confirmed + " confirmed", reconPass);

        System.out.println("----------------------------------------------------------------------");
        System.out.println("Reconciliation Invariant: " + available + " (avail) + " + held + " (held) + " + confirmed + " (conf) == " + totalSeats + " total");
        System.out.println(BOLD + CYAN + "======================================================================" + RESET);

        executor.shutdown();

        boolean allPassed = p1Pass && p2Pass && p3Pass && p4Pass && reconPass;
        if (allPassed) {
            System.out.println("\n" + BOLD + GREEN + ">>> OVERALL RESULT: ALL PHASES PASSED WITH ZERO 5xx ERRORS <<<" + RESET + "\n");
        } else {
            System.out.println("\n" + BOLD + RED + ">>> OVERALL RESULT: BURST CONCURRENCY CHECK FAILED <<<" + RESET + "\n");
            System.exit(1);
        }
    }

    private static void printPhaseRow(String name, String outcome, boolean pass) {
        String badge = pass ? (GREEN + "PASS" + RESET) : (RED + "FAIL" + RESET);
        System.out.printf("%-38s | %-18s | %s\n", name, outcome, badge);
    }

    private static String extractJsonValue(String json, String... keys) {
        for (String key : keys) {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^,\"}]+)\"?");
            Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return "0";
    }

    private static String extractNestedJsonValue(String json, String parentKey, String childKey) {
        Pattern parentPattern = Pattern.compile("\"" + parentKey + "\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher parentMatcher = parentPattern.matcher(json);
        if (parentMatcher.find()) {
            String nested = parentMatcher.group(1);
            Pattern childPattern = Pattern.compile("\"" + childKey + "\"\\s*:\\s*\"?([^,\"}]+)\"?");
            Matcher childMatcher = childPattern.matcher(nested);
            if (childMatcher.find()) {
                return childMatcher.group(1).trim();
            }
        }
        return "0";
    }
}
