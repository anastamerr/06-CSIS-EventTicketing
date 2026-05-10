package com.team06.eventticketing.booking.client;

import com.team06.eventticketing.booking.dto.UserBookingSummaryDTO;
import com.team06.eventticketing.booking.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.team06.eventticketing.booking.dto.UserBookingSummaryDTO;
@FeignClient(
        name = "user-service",
        url = "${feign.user-service.url}",
        configuration = FeignAuthConfig.class
)
public interface UserServiceClient {

    @GetMapping("/api/users/{id}")
    UserDTO getUser(@PathVariable Long id);

    @GetMapping("/api/users/{userId}/booking-summary")
    UserBookingSummaryDTO getUserBookingSummary(@PathVariable Long userId);
}