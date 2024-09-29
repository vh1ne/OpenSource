package com.vh1ne.OpenSource.xpnl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class XTotalPNL {

    @lombok.Data
    public static class Response {
        public Data data;
        public boolean status;
    }

    @lombok.Data
    public static class Data {
        public List<HistoryItem> history;
        @JsonProperty("creator_profile")
        public CreatorProfile creatorProfile;
    }

    @lombok.Data
    public static class HistoryItem {
        @JsonProperty("short_id")
        public String shortId;
        @JsonProperty("created_at")
        public OffsetDateTime createdAt;
        @JsonProperty("total_profit")
        public double totalProfit;
        @JsonProperty("is_no_tradeday")
        public boolean isNoTradeday;
    }

    @lombok.Data
    public static class CreatorProfile {
        @JsonProperty("broker_id")
        public int brokerId;
        @JsonProperty("twitter_profile")
        public TwitterProfile twitterProfile;
        @JsonProperty("followers_count")
        public int followersCount;
        @JsonProperty("total_snapshots_count")
        public int totalSnapshotsCount;
        @JsonProperty("live_share_mode")
        public String liveShareMode;
        @JsonProperty("live_since")
        public OffsetDateTime liveSince;
        public String name;
    }

    @lombok.Data
    public static class TwitterProfile {
        public String id;
        public String name;
        public String username;
        @JsonProperty("profile_image_url")
        public String profileImageUrl;
        @JsonProperty("word_hash")
        public String wordHash;
    }

    public static Response fromUrl(String url) {
        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.findAndRegisterModules(); // This is needed for LocalDateTime deserialization
                return mapper.readValue(response.body(), Response.class);
            } else {
                System.err.println("HTTP request failed with status code: " + response.statusCode());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Calculate and print the summary of PNL data.
     * 
     * @param publicURL the public URL to fetch PNL data
     */
    public static void calculateSummary(String publicURL) {
        var username = publicURL.replaceFirst("^https?://[^/]+/verified-pnl/", "").split("/")[0];
        var url = "https://api.sensibull.com/v1/positions_snapshots_list/" + username;
        var pnl = fromUrl(url);

        assert pnl != null;
        Map<Integer, Map<Integer, DoubleSummaryStatistics>> yearMonthStats = pnl.getData().getHistory().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        item -> item.getCreatedAt().getYear(),
                        Collectors.groupingBy(
                                item -> item.getCreatedAt().getMonthValue(),
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        list -> {
                                            Map<Integer, HistoryItem> latestPerDay = list.stream()
                                                    .collect(Collectors.toMap(
                                                            item -> item.getCreatedAt().getDayOfMonth(),
                                                            item -> item,
                                                            (existing, replacement) -> existing.getCreatedAt().isAfter(replacement.getCreatedAt()) ? existing : replacement
                                                    ));
                                            return latestPerDay.values().stream()
                                                    .collect(Collectors.summarizingDouble(HistoryItem::getTotalProfit));
                                        }
                                )
                        )
                ));

        DoubleSummaryStatistics allYearsStats = new DoubleSummaryStatistics();
        AtomicLong totalProfitableDays = new AtomicLong();
        AtomicLong totalLossDays = new AtomicLong();

        yearMonthStats.forEach((year, monthStats) -> {
            System.out.println("Year: " + year);
            int yearProfitableDays = 0;
            int yearLossDays = 0;
            int yearTotalDays = 0;

            for (Map.Entry<Integer, DoubleSummaryStatistics> entry : monthStats.entrySet()) {
                int month = entry.getKey();
                DoubleSummaryStatistics stats = entry.getValue();

                long profitableDays = pnl.getData().getHistory().stream()
                        .filter(item -> item.getCreatedAt().getYear() == year &&
                                item.getCreatedAt().getMonthValue() == month)
                        .collect(Collectors.groupingBy(
                                item -> item.getCreatedAt().toLocalDate(),
                                Collectors.maxBy(Comparator.comparing(HistoryItem::getCreatedAt))
                        ))
                        .values()
                        .stream()
                        .filter(optItem -> optItem.isPresent() && optItem.get().getTotalProfit() > 0)
                        .count();
                long lossDays = stats.getCount() - profitableDays;

                yearProfitableDays += profitableDays;
                yearLossDays += lossDays;
                yearTotalDays += stats.getCount();

                System.out.printf("  Month: %02d, Total Profit: %.2f, Daily avg: %.2f, Count: %d, Profitable: %d, Loss: %d%n",
                        month, stats.getSum(), stats.getAverage(), stats.getCount(), profitableDays, lossDays);
            }

            DoubleSummaryStatistics yearStats = monthStats.values().stream()
                    .collect(Collectors.summarizingDouble(DoubleSummaryStatistics::getSum));
            System.out.printf("  Year Total: %.2f, Monthly Avg: %.2f, Total Days: %d, Profitable: %d, Loss: %d%n%n",
                    yearStats.getSum(), yearStats.getAverage(), yearTotalDays, yearProfitableDays, yearLossDays);

            allYearsStats.combine(yearStats);
            totalProfitableDays.addAndGet(yearProfitableDays);
            totalLossDays.addAndGet(yearLossDays);
        });

        long totalDays = totalProfitableDays.get() + totalLossDays.get();
        System.out.println("Summary for all years: for User: " + username);
        System.out.printf("Total Profit: ₹%s, Total Days: %d, Profitable: %d, Loss: %d%n",
                String.format("%,.2f", allYearsStats.getSum()),
                totalDays,
                totalProfitableDays.get(), totalLossDays.get());

        printAllDaysPNLInAscendingOrder(pnl);
        printAggregateByDayOfWeek(pnl);
        printDayOfMonthStatistics(pnl);
    }

    /**
     * Print all days' PNL in ascending order.
     * 
     * @param pnl the PNL response data
     */
    public static void printAllDaysPNLInAscendingOrder(Response pnl) {
        pnl.getData().getHistory().stream()
                .sorted(Comparator.comparing(HistoryItem::getCreatedAt))
                .forEach(item -> System.out.printf("Date: %s, Profit: %.2f%n", item.getCreatedAt(), item.getTotalProfit()));
    }

    /**
     * Print aggregate PNL by day of the week.
     * 
     * @param pnl the PNL response data
     */
    public static void printAggregateByDayOfWeek(Response pnl) {
        Map<DayOfWeek, DoubleSummaryStatistics> dayOfWeekStats = pnl.getData().getHistory().stream()
                .collect(Collectors.groupingBy(
                        item -> item.getCreatedAt().getDayOfWeek(),
                        Collectors.summarizingDouble(HistoryItem::getTotalProfit)
                ));

        dayOfWeekStats.forEach((dayOfWeek, stats) -> {
            System.out.printf("Day of Week: %s, Total Profit: %.2f, Average Profit: %.2f, Count: %d%n",
                    dayOfWeek, stats.getSum(), stats.getAverage(), stats.getCount());
        });
    }

    /**
     * Print statistics for the day of the month with the most profit and the most loss.
     * 
     * @param pnl the PNL response data
     */
    public static void printDayOfMonthStatistics(Response pnl) {
        Map<Integer, DoubleSummaryStatistics> dayOfMonthStats = pnl.getData().getHistory().stream()
                .collect(Collectors.groupingBy(
                        item -> item.getCreatedAt().getDayOfMonth(),
                        Collectors.summarizingDouble(HistoryItem::getTotalProfit)
                ));

        Optional<Map.Entry<Integer, DoubleSummaryStatistics>> maxProfitDay = dayOfMonthStats.entrySet().stream()
                .max(Comparator.comparingDouble(entry -> entry.getValue().getSum()));

        Optional<Map.Entry<Integer, DoubleSummaryStatistics>> maxLossDay = dayOfMonthStats.entrySet().stream()
                .min(Comparator.comparingDouble(entry -> entry.getValue().getSum()));

        maxProfitDay.ifPresent(entry -> System.out.printf("Day of Month with Most Profit: %d, Total Profit: %.2f%n",
                entry.getKey(), entry.getValue().getSum()));

        maxLossDay.ifPresent(entry -> System.out.printf("Day of Month with Most Loss: %d, Total Loss: %.2f%n",
                entry.getKey(), entry.getValue().getSum()));
    }
}
