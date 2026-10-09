package com.example.finance;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Entity @Table(name="app_users") class AppUser implements UserDetails {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @NotBlank public String name;
    @Email @NotBlank @Column(unique=true) public String email;
    @NotBlank public String role = "USER";
    @NotBlank public String status = "ACTIVE";
    @JsonIgnore public String password;
    protected AppUser() {}
    AppUser(String name,String email,String role,String password){this.name=name;this.email=email;this.role=role;this.password=password;}
    @Override @JsonIgnore public Collection<? extends GrantedAuthority> getAuthorities(){return List.of(new SimpleGrantedAuthority("ROLE_"+role));}
    @Override @JsonIgnore public String getPassword(){return password;}
    @Override public String getUsername(){return email;}
    @Override @JsonIgnore public boolean isAccountNonExpired(){return true;}
    @Override @JsonIgnore public boolean isAccountNonLocked(){return "ACTIVE".equals(status);}
    @Override @JsonIgnore public boolean isCredentialsNonExpired(){return true;}
    @Override @JsonIgnore public boolean isEnabled(){return "ACTIVE".equals(status);}
}
@Entity @Table(name="expenses",indexes=@Index(name="idx_expenses_user_date",columnList="user_id,date")) class Expense {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @NotBlank public String description;
    @NotBlank public String category;
    @NotNull @DecimalMin("0.01") @Column(nullable=false,precision=19,scale=2) public BigDecimal amount;
    @NotNull @Column(nullable=false) public LocalDate date;
    @NotNull @Column(nullable=false,name="user_id") public Long userId;
    protected Expense() {}
}
@Entity @Table(name="budgets",indexes=@Index(name="idx_budgets_user",columnList="user_id")) class Budget {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @NotBlank public String category;
    @NotNull @DecimalMin("0.01") @Column(nullable=false,precision=19,scale=2) public BigDecimal amount;
    @NotBlank @Column(nullable=false) public String duration;
    @NotNull @Column(nullable=false,name="user_id") public Long userId;
    protected Budget() {}
}
@Entity @Table(name="financial_goals",indexes=@Index(name="idx_goals_user",columnList="user_id")) class FinancialGoal {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @NotBlank public String description;
    @NotNull @DecimalMin("0.01") @Column(nullable=false,precision=19,scale=2) public BigDecimal targetAmount;
    @NotNull @DecimalMin("0.00") @Column(nullable=false,precision=19,scale=2) public BigDecimal currentAmount = BigDecimal.ZERO;
    @NotNull @Column(nullable=false) public LocalDate deadline;
    @NotNull @Column(nullable=false,name="user_id") public Long userId;
    protected FinancialGoal() {}
}
@Entity @Table(name="system_settings") class SystemSetting {
    @Id public String settingKey;
    @NotBlank public String settingValue;
    protected SystemSetting() {}
    SystemSetting(String key,String value){settingKey=key;settingValue=value;}
}
