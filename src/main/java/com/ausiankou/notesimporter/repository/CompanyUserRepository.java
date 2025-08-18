package com.ausiankou.notesimporter.repository;

import com.ausiankou.notesimporter.entity.CompanyUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyUserRepository extends JpaRepository<CompanyUser, Long> {
    Optional<CompanyUser> findByLogin(String login);
}
