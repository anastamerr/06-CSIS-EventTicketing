package com.team06.eventticketing.contracts.dto;

public record UserDTO(Long id, String name, String email, String role) {

    public UserDTO(Long id, String email, String role) {
        this(id, null, email, role);
    }
}
