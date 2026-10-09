package com.example.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record UserResponse(Long id, String name, String email, String role, String status) {
    static UserResponse from(AppUser user) { return new UserResponse(user.id, user.name, user.email, user.role, user.status); }
}
record RegisterRequest(@NotBlank String name, @Email @NotBlank String email, @NotBlank @Size(min=8) String password) {}

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    AuthController(UserRepository users, PasswordEncoder passwordEncoder) { this.users = users; this.passwordEncoder = passwordEncoder; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().toLowerCase();
        var existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            AppUser legacyUser = existing.get();
            if (legacyUser.password != null || !"USER".equals(legacyUser.role)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
            legacyUser.password = passwordEncoder.encode(request.password());
            return UserResponse.from(users.save(legacyUser));
        }
        AppUser user = new AppUser(request.name(), email, "USER", passwordEncoder.encode(request.password()));
        user.status = "ACTIVE";
        return UserResponse.from(users.save(user));
    }

    @GetMapping("/me")
    UserResponse currentUser(@AuthenticationPrincipal AppUser user) { return UserResponse.from(user); }
}
