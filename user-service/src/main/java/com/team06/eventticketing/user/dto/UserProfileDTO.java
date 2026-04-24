package com.team06.eventticketing.user.dto;

import java.util.List;
import java.util.Map;

public class UserProfileDTO {

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private Map<String, Object> preferences;
    private List<VenueDTO> favoriteVenues;
    private int totalFavoriteVenues;

    public UserProfileDTO() {
    }

    public UserProfileDTO(Long userId, String name, String email, String phone,
                          Map<String, Object> preferences, List<VenueDTO> favoriteVenues) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.preferences = preferences;
        this.favoriteVenues = favoriteVenues;
        this.totalFavoriteVenues = favoriteVenues != null ? favoriteVenues.size() : 0;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Map<String, Object> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, Object> preferences) {
        this.preferences = preferences;
    }

    public List<VenueDTO> getFavoriteVenues() {
        return favoriteVenues;
    }

    public void setFavoriteVenues(List<VenueDTO> favoriteVenues) {
        this.favoriteVenues = favoriteVenues;
        this.totalFavoriteVenues = favoriteVenues != null ? favoriteVenues.size() : 0;
    }

    public int getTotalFavoriteVenues() {
        return totalFavoriteVenues;
    }

    public void setTotalFavoriteVenues(int totalFavoriteVenues) {
        this.totalFavoriteVenues = totalFavoriteVenues;
    }

    public static class VenueDTO {
        private Long id;
        private String label;
        private String venueName;
        private String location;
        private Integer capacity;
        private Boolean isDefault;
        private Map<String, Object> metadata;

        public VenueDTO() {
        }

        public VenueDTO(Long id, String label, String venueName, String location,
                        Integer capacity, Boolean isDefault, Map<String, Object> metadata) {
            this.id = id;
            this.label = label;
            this.venueName = venueName;
            this.location = location;
            this.capacity = capacity;
            this.isDefault = isDefault;
            this.metadata = metadata;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getVenueName() {
            return venueName;
        }

        public void setVenueName(String venueName) {
            this.venueName = venueName;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public Integer getCapacity() {
            return capacity;
        }

        public void setCapacity(Integer capacity) {
            this.capacity = capacity;
        }

        public Boolean getIsDefault() {
            return isDefault;
        }

        public void setIsDefault(Boolean isDefault) {
            this.isDefault = isDefault;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        public static VenueBuilder builder() {
            return new VenueBuilder();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long userId;
        private String name;
        private String email;
        private String phone;
        private Map<String, Object> preferences;
        private List<VenueDTO> favoriteVenues;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder preferences(Map<String, Object> preferences) {
            this.preferences = preferences;
            return this;
        }

        public Builder favoriteVenues(List<VenueDTO> favoriteVenues) {
            this.favoriteVenues = favoriteVenues;
            return this;
        }

        public UserProfileDTO build() {
            return new UserProfileDTO(userId, name, email, phone, preferences, favoriteVenues);
        }
    }

    public static final class VenueBuilder {
        private Long id;
        private String label;
        private String venueName;
        private String location;
        private Integer capacity;
        private Boolean isDefault;
        private Map<String, Object> metadata;

        public VenueBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public VenueBuilder label(String label) {
            this.label = label;
            return this;
        }

        public VenueBuilder venueName(String venueName) {
            this.venueName = venueName;
            return this;
        }

        public VenueBuilder location(String location) {
            this.location = location;
            return this;
        }

        public VenueBuilder capacity(Integer capacity) {
            this.capacity = capacity;
            return this;
        }

        public VenueBuilder isDefault(Boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        public VenueBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public VenueDTO build() {
            return new VenueDTO(id, label, venueName, location, capacity, isDefault, metadata);
        }
    }
}
