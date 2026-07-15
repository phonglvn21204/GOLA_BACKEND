package com.gola.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "scrapingdog")
public class ScrapingDogProperties {
    private boolean enabled = false;
    private String apiKey;
    private String baseUrl = "https://api.scrapingdog.com/google";
    private String country = "vn";
    private String domain = "google.com";
    private boolean combinedOutput = true;
    private int timeoutMs = 8000;
    private int maxQueriesPerTrip = 1;
    private boolean cacheEnabled = true;
    private int cacheTtlDays = 14;
    private boolean onlyForPremium = false;
}
