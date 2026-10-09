package com.example.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

record AdminCreateUserRequest(@NotBlank String name, @Email @NotBlank String email,
                              @NotBlank String role, @NotBlank @Size(min=8) String password) {}
record UpdateUserRequest(@NotBlank String name, @Email @NotBlank String email,
                         @NotBlank String role, @NotBlank String status) {}

@RestController
@RequestMapping("/api")
class FinanceController {
    private final UserRepository users;
    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final GoalRepository goals;
    private final SettingRepository settings;
    private final PasswordEncoder passwordEncoder;

    FinanceController(UserRepository users, ExpenseRepository expenses, BudgetRepository budgets,
                      GoalRepository goals, SettingRepository settings, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.expenses = expenses;
        this.budgets = budgets;
        this.goals = goals;
        this.settings = settings;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/users")
    List<UserResponse> users() { return users.findAll().stream().map(UserResponse::from).toList(); }

    @GetMapping("/users/{id}")
    UserResponse user(@PathVariable Long id) { return UserResponse.from(findUser(id)); }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        if (users.existsByEmail(request.email())) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
        String role = request.role().toUpperCase();
        if (!List.of("USER", "ADMIN").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be USER or ADMIN");
        AppUser user = new AppUser(request.name(), request.email().toLowerCase(), role, passwordEncoder.encode(request.password()));
        return UserResponse.from(users.save(user));
    }

    @PutMapping("/users/{id}")
    UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        AppUser user = findUser(id);
        if (!user.email.equalsIgnoreCase(request.email()) && users.existsByEmail(request.email())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
        String role = request.role().toUpperCase();
        if (!List.of("USER", "ADMIN").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be USER or ADMIN");
        String status = request.status().toUpperCase();
        if (!List.of("ACTIVE", "INACTIVE").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or INACTIVE");
        user.name = request.name();
        user.email = request.email().toLowerCase();
        user.role = role;
        user.status = status;
        return UserResponse.from(users.save(user));
    }

    @PatchMapping("/users/{id}/status")
    UserResponse userStatus(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication authentication) {
        String status = body.getOrDefault("status", "").toUpperCase();
        if (!List.of("ACTIVE", "INACTIVE").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or INACTIVE");
        AppUser user = findUser(id);
        if (user.id.equals(authenticatedUser(authentication).id) && status.equals("INACTIVE")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot deactivate the account currently in use");
        user.status = status;
        return UserResponse.from(users.save(user));
    }

    @PatchMapping("/users/{id}/role")
    UserResponse userRole(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication authentication) {
        String role = body.getOrDefault("role", "").toUpperCase();
        if (!List.of("USER", "ADMIN").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be USER or ADMIN");
        AppUser user = findUser(id);
        AppUser actor = authenticatedUser(authentication);
        if (actor.id.equals(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot change your own role");
        if (user.role.equals("ADMIN") && role.equals("USER") && users.countByRole("ADMIN") <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The last administrator cannot be demoted");
        }
        user.role = role;
        return UserResponse.from(users.save(user));
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteUser(@PathVariable Long id, Authentication authentication) {
        findUser(id);
        if (authenticatedUser(authentication).id.equals(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete the account currently in use");
        expenses.deleteByUserId(id);
        budgets.deleteByUserId(id);
        goals.deleteByUserId(id);
        users.deleteById(id);
    }

    @GetMapping("/expenses")
    List<Expense> expenses(@RequestParam(required=false) Long userId, Authentication authentication) {
        return expenses.findByUserIdOrderByDateDesc(resolveOwner(authentication, userId));
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    Expense createExpense(@Valid @RequestBody Expense expense, Authentication authentication) {
        expense.id = null;
        expense.userId = resolveOwner(authentication, expense.userId);
        return expenses.save(expense);
    }

    @PutMapping("/expenses/{id}")
    Expense updateExpense(@PathVariable Long id, @Valid @RequestBody Expense expense, Authentication authentication) {
        Expense existing = expenses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireOwner(authentication, existing.userId);
        expense.userId = resolveOwner(authentication, expense.userId == null ? existing.userId : expense.userId);
        expense.id = id;
        return expenses.save(expense);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExpense(@PathVariable Long id, Authentication authentication) {
        Expense expense = expenses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireOwner(authentication, expense.userId);
        expenses.delete(expense);
    }

    @GetMapping("/budgets")
    List<Budget> budgets(@RequestParam(required=false) Long userId, Authentication authentication) {
        return budgets.findByUserId(resolveOwner(authentication, userId));
    }

    @PostMapping("/budgets")
    @ResponseStatus(HttpStatus.CREATED)
    Budget createBudget(@Valid @RequestBody Budget budget, Authentication authentication) {
        budget.id = null;
        budget.userId = resolveOwner(authentication, budget.userId);
        return budgets.save(budget);
    }

    @PutMapping("/budgets/{id}")
    Budget updateBudget(@PathVariable Long id, @Valid @RequestBody Budget budget, Authentication authentication) {
        Budget existing = budgets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireOwner(authentication, existing.userId);
        budget.userId = resolveOwner(authentication, budget.userId == null ? existing.userId : budget.userId);
        budget.id = id;
        return budgets.save(budget);
    }

    @DeleteMapping("/budgets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteBudget(@PathVariable Long id, Authentication authentication) {
        Budget budget = budgets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireOwner(authentication, budget.userId);
        budgets.delete(budget);
    }

    @GetMapping("/goals")
    List<FinancialGoal> goals(@RequestParam(required=false) Long userId, Authentication authentication) {
        return goals.findByUserId(resolveOwner(authentication, userId));
    }

    @PostMapping("/goals")
    @ResponseStatus(HttpStatus.CREATED)
    FinancialGoal createGoal(@Valid @RequestBody FinancialGoal goal, Authentication authentication) {
        goal.id = null;
        goal.userId = resolveOwner(authentication, goal.userId);
        return goals.save(goal);
    }

    @PutMapping("/goals/{id}")
    FinancialGoal updateGoal(@PathVariable Long id, @Valid @RequestBody FinancialGoal goal, Authentication authentication) {
        FinancialGoal existing = goals.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireOwner(authentication, existing.userId);
        goal.userId = resolveOwner(authentication, goal.userId == null ? existing.userId : goal.userId);
        goal.id = id;
        return goals.save(goal);
    }

    @DeleteMapping("/goals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGoal(@PathVariable Long id, Authentication authentication) {
        FinancialGoal goal = goals.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireOwner(authentication, goal.userId);
        goals.delete(goal);
    }

    @GetMapping("/admin/settings")
    List<SystemSetting> settings() { return settings.findAll(); }

    @PutMapping("/admin/settings/{key}")
    SystemSetting updateSetting(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value is required");
        return settings.save(new SystemSetting(key, value));
    }

    private AppUser findUser(Long id) { return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")); }
    private AppUser authenticatedUser(Authentication authentication) { return (AppUser) authentication.getPrincipal(); }
    private Long resolveOwner(Authentication authentication, Long requestedUserId) {
        AppUser user = authenticatedUser(authentication);
        if (user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            Long owner = requestedUserId == null ? user.id : requestedUserId;
            if (!users.existsById(owner)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown userId");
            return owner;
        }
        if (requestedUserId != null && !requestedUserId.equals(user.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may only access your own financial records");
        return user.id;
    }
    private void requireOwner(Authentication authentication, Long recordOwnerId) {
        AppUser user = authenticatedUser(authentication);
        if (!recordOwnerId.equals(user.id) && user.getAuthorities().stream().noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may only access your own financial records");
        }
    }
}
