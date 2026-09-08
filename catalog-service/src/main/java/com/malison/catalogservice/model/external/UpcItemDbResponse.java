package com.malison.catalogservice.model.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Maps the response shape of the UPCitemdb trial lookup API
 * (https://api.upcitemdb.com/prod/trial/lookup?upc=...).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpcItemDbResponse {
    private String code;
    private Integer total;
    private List<Item> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String title;
        private String description;
        private String brand;
        private String category;

        @JsonProperty("lowest_recorded_price")
        private Double lowestRecordedPrice;

        @JsonProperty("highest_recorded_price")
        private Double highestRecordedPrice;
    }
}
