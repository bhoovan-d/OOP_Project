package edu.oop.itsm.web.dto;

import edu.oop.itsm.domain.Role;
import jakarta.validation.constraints.*;

public final class AuthDtos {

    private AuthDtos() {}

    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-72 characters and contain a letter and a digit";

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 100) String fullName,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password,
            @Size(max = 60) String department,
            @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "Invalid phone number") String phone) {}

    public record Profile(Long id, String code, String fullName, String email, Role role, String department, String phone, boolean itManager) {}

    public record TokenResponse(String token, long expiresInSeconds, Profile user) {}
}
