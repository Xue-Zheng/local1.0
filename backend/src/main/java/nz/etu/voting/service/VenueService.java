package nz.etu.voting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * BMM Venue Management Service
 * Manages 27 meeting venues across three regions for E tū Union
 */
@Service
public class VenueService {

    private static final Logger logger = LoggerFactory.getLogger(VenueService.class);

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode venueConfig;
    private Map<String, List<Map<String, Object>>> venuesByRegion;
    private Map<Integer, Map<String, Object>> venuesById;

    @PostConstruct
    public void init() {
        loadVenueConfiguration();
    }

    private void loadVenueConfiguration() {
        try {
            // Load from classpath or file system
            Resource resource = resourceLoader.getResource("file:bmm-venues-config.json");
            if (!resource.exists()) {
                // Fallback to classpath
                resource = resourceLoader.getResource("classpath:bmm-venues-config.json");
            }

            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    venueConfig = objectMapper.readTree(inputStream);
                    buildVenueMaps();
                    logger.info("Successfully loaded BMM venue configuration with {} venues",
                            venueConfig.path("meta").path("totalVenues").asInt());
                }
            } else {
                logger.warn("BMM venue configuration file not found, creating default configuration");
                createDefaultConfiguration();
            }
        } catch (IOException e) {
            logger.error("Failed to load venue configuration: {}", e.getMessage());
            createDefaultConfiguration();
        }
    }

    private void buildVenueMaps() {
        venuesByRegion = new HashMap<>();
        venuesById = new HashMap<>();

        // Handle new flat structure
        JsonNode venues = venueConfig.path("venues");
        if (venues.isArray()) {
            // New structure with flat venue list
            for (JsonNode venue : venues) {
                Map<String, Object> venueMap = objectMapper.convertValue(venue, Map.class);
                venuesById.put(venue.path("id").asInt(), venueMap);
            }
            logger.info("Loaded {} venues from flat structure", venuesById.size());
        } else {
            // Old structure with regions
            JsonNode regions = venueConfig.path("regions");
            regions.fieldNames().forEachRemaining(regionName -> {
                List<Map<String, Object>> regionVenues = new ArrayList<>();
                JsonNode venuesNode = regions.path(regionName).path("venues");

                for (JsonNode venue : venuesNode) {
                    Map<String, Object> venueMap = objectMapper.convertValue(venue, Map.class);
                    regionVenues.add(venueMap);
                    venuesById.put(venue.path("id").asInt(), venueMap);
                }

                venuesByRegion.put(regionName, regionVenues);
            });
        }
    }

    private void createDefaultConfiguration() {
        // Create minimal default configuration
        Map<String, Object> defaultConfig = new HashMap<>();
        Map<String, Object> regions = new HashMap<>();

        // Add minimal venue data for each region
        for (String region : Arrays.asList("Northern Region", "Central Region", "Southern Region")) {
            Map<String, Object> regionData = new HashMap<>();
            regionData.put("color", region.contains("Northern") ? "blue" :
                    region.contains("Central") ? "green" : "pink");
            regionData.put("venues", new ArrayList<>());
            regions.put(region, regionData);
        }

        defaultConfig.put("regions", regions);

        Map<String, Object> meta = new HashMap<>();
        meta.put("totalVenues", 0);
        meta.put("specialVoteEligible", Arrays.asList("Southern Region"));
        defaultConfig.put("meta", meta);

        try {
            venueConfig = objectMapper.valueToTree(defaultConfig);
            buildVenueMaps();
        } catch (Exception e) {
            logger.error("Failed to create default venue configuration: {}", e.getMessage());
        }
    }

    /**
     * Get all venues for a specific region
     */
    public List<Map<String, Object>> getVenuesByRegion(String region) {
        return venuesByRegion.getOrDefault(region, new ArrayList<>());
    }

    /**
     * Get venue by ID
     */
    public Map<String, Object> getVenueById(int venueId) {
        return venuesById.get(venueId);
    }

    /**
     * Get all regions with their venue counts
     */
    public Map<String, Object> getRegionSummary() {
        Map<String, Object> summary = new HashMap<>();

        for (String region : venuesByRegion.keySet()) {
            Map<String, Object> regionInfo = new HashMap<>();
            regionInfo.put("venueCount", venuesByRegion.get(region).size());
            regionInfo.put("color", venueConfig.path("regions").path(region).path("color").asText());
            regionInfo.put("venues", venuesByRegion.get(region));
            summary.put(region, regionInfo);
        }

        return summary;
    }

    /**
     * Check if a region is eligible for special voting
     */
    public boolean isSpecialVoteEligible(String region) {
        JsonNode eligibleRegions = venueConfig.path("meta").path("specialVoteEligible");
        for (JsonNode eligibleRegion : eligibleRegions) {
            if (eligibleRegion.asText().equals(region)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all venue options for dropdowns/selection
     */
    public List<Map<String, Object>> getAllVenueOptions() {
        List<Map<String, Object>> allVenues = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, Object>>> entry : venuesByRegion.entrySet()) {
            String region = entry.getKey();
            for (Map<String, Object> venue : entry.getValue()) {
                Map<String, Object> venueOption = new HashMap<>(venue);
                venueOption.put("region", region);
                venueOption.put("fullName", venue.get("name") + " - " + venue.get("venue"));
                allVenues.add(venueOption);
            }
        }

        // Sort by region then by date
        allVenues.sort((v1, v2) -> {
            int regionCompare = ((String) v1.get("region")).compareTo((String) v2.get("region"));
            if (regionCompare != 0) return regionCompare;
            return ((String) v1.get("date")).compareTo((String) v2.get("date"));
        });

        return allVenues;
    }

    /**
     * Get venue statistics
     */
    public Map<String, Object> getVenueStatistics() {
        Map<String, Object> stats = new HashMap<>();

        int totalVenues = venuesById.size();
        int totalCapacity = venuesById.values().stream()
                .mapToInt(v -> {
                    Object capacity = v.get("capacity");
                    return capacity != null ? (Integer) capacity : 0;
                })
                .sum();

        stats.put("totalVenues", totalVenues);
        stats.put("totalCapacity", totalCapacity);
        stats.put("byRegion", venueConfig.path("meta").path("regions"));
        stats.put("specialVoteEligibleRegions", venueConfig.path("meta").path("specialVoteEligible"));

        return stats;
    }

    /**
     * Find venues by criteria
     */
    public List<Map<String, Object>> findVenues(String region, String searchTerm) {
        List<Map<String, Object>> results = new ArrayList<>();

        List<Map<String, Object>> venues = region != null ?
                getVenuesByRegion(region) : getAllVenueOptions();

        for (Map<String, Object> venue : venues) {
            if (searchTerm == null || searchTerm.isEmpty() ||
                    venue.get("name").toString().toLowerCase().contains(searchTerm.toLowerCase()) ||
                    venue.get("venue").toString().toLowerCase().contains(searchTerm.toLowerCase()) ||
                    venue.get("address").toString().toLowerCase().contains(searchTerm.toLowerCase())) {
                results.add(venue);
            }
        }

        return results;
    }

    /**
     * Get venue configuration for frontend
     */
    public JsonNode getVenueConfiguration() {
        return venueConfig;
    }

    /**
     * Get venue by forum description
     */
    public Map<String, Object> getVenueByForumDesc(String forumDesc) {
        if (forumDesc == null || forumDesc.trim().isEmpty()) {
            return null;
        }

        JsonNode venues = venueConfig.path("venues");
        if (venues.isArray()) {
            for (JsonNode venue : venues) {
                if (forumDesc.equals(venue.path("forumDesc").asText())) {
                    return objectMapper.convertValue(venue, Map.class);
                }
            }
        }

        logger.warn("No venue found for forum_desc: {}", forumDesc);
        return null;
    }

    /**
     * Get venue options based on forum description
     * Handles special cases like Greymouth and Whangarei that map to multiple venues
     */
    public List<Map<String, Object>> getVenueOptionsByForumDesc(String forumDesc) {
        if (forumDesc == null || forumDesc.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> options = new ArrayList<>();

        // Check if there's a special mapping for this forum
        JsonNode forumVenueMapping = venueConfig.path("forumVenueMapping").path(forumDesc);
        if (forumVenueMapping.isArray()) {
            // Found special mapping
            for (JsonNode venueOption : forumVenueMapping) {
                Map<String, Object> option = new HashMap<>();
                option.put("venueName", venueOption.path("venueName").asText());
                option.put("fullName", venueOption.path("fullName").asText());
                option.put("address", venueOption.path("address").asText());
                option.put("date", venueOption.path("date").asText());
                option.put("capacity", venueOption.path("capacity").asInt(0));
                option.put("forumDesc", forumDesc);
                options.add(option);
            }
            logger.info("Found {} venue options for forum {}", options.size(), forumDesc);
        } else {
            // No special mapping, return the standard venue
            Map<String, Object> standardVenue = getVenueByForumDesc(forumDesc);
            if (standardVenue != null) {
                options.add(standardVenue);
            }
        }

        return options;
    }
}