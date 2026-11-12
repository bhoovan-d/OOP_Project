import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal load generator (JDK only). Logs in as admin, then fires authenticated GETs.
 * Run against a started server:  java loadtest/LoadTest.java [requests] [concurrency] [baseUrl]
 */
public class LoadTest {
    public static void main(String[] args) throws Exception {
        int total = args.length > 0 ? Integer.parseInt(args[0]) : 20000;
        int concurrency = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        String base = args.length > 2 ? args[2] : "http://localhost:8080";
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        String login = http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"admin@itsm.local\",\"password\":\"Admin@123\"}")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        String token = login.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        String[] paths = {"/api/incidents?size=20", "/api/incidents?open=true&priority=P1&priority=P2", "/api/incidents?q=vpn", "/api/catalog", "/api/notifications"};

        Semaphore gate = new Semaphore(concurrency);
        AtomicInteger ok = new AtomicInteger(), failed = new AtomicInteger();
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        long start = System.nanoTime();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < total; i++) {
                String path = paths[i % paths.length];
                gate.acquire();
                pool.submit(() -> {
                    long t = System.nanoTime();
                    try {
                        int code = http.send(HttpRequest.newBuilder(URI.create(base + path)).header("Authorization", "Bearer " + token).build(),
                                HttpResponse.BodyHandlers.discarding()).statusCode();
                        (code == 200 ? ok : failed).incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    } finally {
                        latencies.add((System.nanoTime() - t) / 1_000_000);
                        gate.release();
                    }
                });
            }
        }
        double secs = (System.nanoTime() - start) / 1e9;
        Collections.sort(latencies);
        System.out.printf("%d requests, concurrency %d: %.1fs, %.0f req/s, ok=%d failed=%d, p50=%dms p95=%dms p99=%dms%n",
                total, concurrency, secs, total / secs, ok.get(), failed.get(),
                latencies.get(latencies.size() / 2), latencies.get((int) (latencies.size() * 0.95)), latencies.get((int) (latencies.size() * 0.99)));
    }
}
