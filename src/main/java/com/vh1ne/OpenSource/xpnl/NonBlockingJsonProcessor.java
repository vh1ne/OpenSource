package com.vh1ne.OpenSource.xpnl;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

public class NonBlockingJsonProcessor {
    private static final String INPUT_FILE = "dummy_requests.txt";
    private static final String OUTPUT_FILE = "output.txt";
    private static final String API_URL = "https://reqres.in/api/users/3";
    private static final int MAX_CONCURRENT_REQUESTS = 100;
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) {
        processFile();
        executor.shutdown();
    }

    /**
     * Reads the file line by line, processes each JSON request concurrently, and writes the response.
     */
    private static void processFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(INPUT_FILE));
             BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE))) {

            CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
            int processedCount = 0;
            int submittedTasks = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                String requestPayload = line; // Capture the request payload
                completionService.submit(() -> sendRequestWithRetry(requestPayload));
                submittedTasks++;

                processedCount++;
                if (processedCount % 500 == 0) {
                    System.out.println("Processed " + processedCount + " requests so far...");
                }
            }

            // Retrieve and write responses as soon as they are available
            for (int i = 0; i < submittedTasks; i++) {
                Future<String> future = completionService.take(); // Waits for the next completed task
                writer.write(future.get());
                writer.newLine();
                if (i % 500 == 0) {
                    writer.flush(); // Flush periodically to ensure data is written
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a JSON request to the external API with a retry mechanism.
     */
    private static String sendRequestWithRetry(String jsonRequest) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return response.body();
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    return "Error: " + e.getMessage();
                }
                try {
                    Thread.sleep(1000); // Wait before retrying
                } catch (InterruptedException ignored) {
                }
            }
        }
        return "Error: Request failed after retries";
    }
}
