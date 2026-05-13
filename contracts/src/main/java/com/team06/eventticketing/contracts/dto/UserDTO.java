package com.team06.eventticketing.contracts.dto;

public record UserDTO(Long id, String name, String email, String role, String status) {

    public UserDTO(Long id, String name, String email, String role) {
        this(id, name, email, role, null);
    }

    public UserDTO(Long id, String email, String role) {
        this(id, null, email, role, null);
    }
}
