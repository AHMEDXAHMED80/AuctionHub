package com.example.auctionhub.auctionhub.dto;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

import com.example.auctionhub.auctionhub.models.User;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * DTO representing a registration request for authentication.
 * Includes user details, credentials, and validation constraints.
 */
public class AuthRegisterRequest {
    /** First name */
    @NotBlank(message = "First name is required")
    private String firstName;
    /** Last name */
    @NotBlank(message = "Last name is required")
    private String lastName;
    /** Username */
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    private String username;
    /** Email address */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;
    /** Birth date */
    @NotNull(message = "Birth date is required")
    private LocalDate birthDate;
    /** Password */
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*]).{6,}$",
        message = "Password must contain at least one uppercase letter, one digit, and one special character (!@#$%^&*)"
    )
    private String password;
    /** User role */
    @Enumerated(EnumType.STRING)
    private User.roles role;
    /** Password confirmation */
    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
