package com.onboarding.diary.controller;

import com.onboarding.diary.dto.UserResponse;
import com.onboarding.diary.entity.User;
import com.onboarding.diary.enums.Role;
import com.onboarding.diary.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/recruits")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<UserResponse>> recruits() {
        List<UserResponse> recruits = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.RECRUIT)
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(recruits);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .fullName(user.getFullName())
                .build();
    }
}
