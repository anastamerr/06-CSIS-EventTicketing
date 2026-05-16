package com.team06.eventticketing.contracts.feign.fallback;

import com.team06.eventticketing.contracts.dto.UserDTO;
import com.team06.eventticketing.contracts.feign.UserServiceClient;
import org.springframework.stereotype.Component;

@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public UserDTO getUser(Long id) {
        return null;
    }
}
