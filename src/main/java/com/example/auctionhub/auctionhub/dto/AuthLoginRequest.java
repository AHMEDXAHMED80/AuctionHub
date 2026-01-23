package com.example.auctionhub.auctionhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * DTO representing a login request for authentication.
 * Includes email and password fields with validation.
 */
public class AuthLoginRequest {
    /** Email address */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;
    /** Password */
    @NotBlank(message = "Password is required")
    private String password;
}
