package com.example.auctionhub.auctionhub.dto;

import com.example.auctionhub.auctionhub.models.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    
    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private LocalDate birthDate;
    private User.roles role;
    private User.status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
