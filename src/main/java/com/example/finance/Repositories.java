package com.example.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
interface UserRepository extends JpaRepository<AppUser,Long> { java.util.Optional<AppUser> findByEmailIgnoreCase(String email); boolean existsByEmail(String email); long countByRole(String role); }
interface ExpenseRepository extends JpaRepository<Expense,Long> { List<Expense> findByUserIdOrderByDateDesc(Long userId); void deleteByUserId(Long userId); }
interface BudgetRepository extends JpaRepository<Budget,Long> { List<Budget> findByUserId(Long userId); void deleteByUserId(Long userId); }
interface GoalRepository extends JpaRepository<FinancialGoal,Long> { List<FinancialGoal> findByUserId(Long userId); void deleteByUserId(Long userId); }
interface SettingRepository extends JpaRepository<SystemSetting,String> {}
