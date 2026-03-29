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

    public UserProfileDTO() {}

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

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Map<String, Object> getPreferences() { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }

    public List<VenueDTO> getFavoriteVenues() { return favoriteVenues; }
    public void setFavoriteVenues(List<VenueDTO> favoriteVenues) {
        this.favoriteVenues = favoriteVenues;
        this.totalFavoriteVenues = favoriteVenues != null ? favoriteVenues.size() : 0;
    }

    public int getTotalFavoriteVenues() { return totalFavoriteVenues; }
    public void setTotalFavoriteVenues(int totalFavoriteVenues) { this.totalFavoriteVenues = totalFavoriteVenues; }

    public static class VenueDTO {
        private String label;
        private String venueName;
        private String location;
        private Integer capacity;
        private Boolean isDefault;
        private Map<String, Object> metadata;

        public VenueDTO() {}

        public VenueDTO(String label, String venueName, String location,
                        Integer capacity, Boolean isDefault, Map<String, Object> metadata) {
            this.label = label;
            this.venueName = venueName;
            this.location = location;
            this.capacity = capacity;
            this.isDefault = isDefault;
            this.metadata = metadata;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getVenueName() { return venueName; }
        public void setVenueName(String venueName) { this.venueName = venueName; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public Integer getCapacity() { return capacity; }
        public void setCapacity(Integer capacity) { this.capacity = capacity; }

        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
}
