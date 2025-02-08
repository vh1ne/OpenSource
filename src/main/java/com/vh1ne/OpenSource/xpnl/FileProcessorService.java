package com.vh1ne.OpenSource.xpnl;




import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Log4j2
public class FileProcessorService {

    Random random = new Random();
    public String generate300k() {
        String outputFileName = "dummy_requests.txt"; // Output file
        String outputFilePath = Paths.get(System.getProperty("user.dir"), outputFileName).toString(); // Full path to the file

        int totalRequests = 300_000; // Number of requests to generate

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            ObjectMapper objectMapper = new ObjectMapper();
               for (int i = 0; i < totalRequests; i++) {
                Map<String, Object> jsonRequest = new HashMap<>();
                jsonRequest.put("id", i + 1);
                jsonRequest.put("name", "User" + (i + 1));
                jsonRequest.put("email", "user" + (i + 1) + "@example.com");
                jsonRequest.put("age", random.nextInt(60) + 18); // Age between 18 and 77
                jsonRequest.put("country", getRandomCountry(random));
                String jsonString = objectMapper.writeValueAsString(jsonRequest);
                writer.write(jsonString);
                writer.newLine();

                if ((i + 1) % 10000 == 0) {
                    log.info("{} requests generated...", i + 1);
                }
            }

            log.info("Dummy requests generated successfully!");
        } catch (IOException e) {
           log.error("Error generating file",e);
        }
        return outputFilePath;
    }

    private static String getRandomCountry(Random random) {
        String[] countries = {"USA", "Canada", "India", "Germany", "France", "Australia", "Brazil", "Japan", "South Korea", "Italy"};
        return countries[random.nextInt(countries.length)];
    }

    private final WebClient webClient = WebClient.create("https://reqres.in/api");

    @SneakyThrows
    public void processFile(String inputFilePath, String outputFilePath) {
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFilePath));
        AtomicInteger counter = new AtomicInteger(0);

        Flux.using(
                        () -> Files.lines(Paths.get(inputFilePath)).iterator(),
                        iterator -> Flux.fromIterable(() -> iterator),
                        iterator -> {}
                )

                .flatMapSequential(this::callExternalApi) // Process in order
                .flatMap(response -> writeToFile(writer, response)) // Write safely
                .doOnNext(count -> {
                    if (counter.incrementAndGet() % 5000 == 0) {
                        log.info("{} records processed...", counter.get());
                    }
                })
                .doFinally(signal -> {
                    try {
                        writer.close();
                        log.info("File writing completed and closed safely.");
                    } catch (IOException e) {
                        log.error("Error writing to file ",e);
                    }
                })
                .subscribe();
    }
    private final Lock lock = new ReentrantLock();
    public Mono<Integer> writeToFile(BufferedWriter writer, String response) {
        return Mono.fromCallable(() -> {
            lock.lock();
            try {
                writer.write(response);
                writer.newLine();
                writer.flush(); // Ensure data is written immediately
            } finally {
                lock.unlock();
            }
            return 1;
        }).subscribeOn(Schedulers.boundedElastic()); // Offload I/O operations
    }

    private Mono<String> callExternalApi(String jsonLine) {
        return webClient.get()
                .uri("/users/3")
                .retrieve()
                .bodyToMono(String.class)
                .delayElement(Duration.ofMillis(150)) // Throttle API calls
                .onErrorResume(e -> {
                    log.error("Error calling API: ", e);
                    return Mono.just("{}"); // Return empty JSON instead of failing
                });
    }

}

