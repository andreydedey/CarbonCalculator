package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.MembershipStatus;
import com.example.carboncalculator.entities.UserInstitution;

public interface UserInstitutionRepository extends JpaRepository<UserInstitution, UUID> {

    List<UserInstitution> findByUserId(UUID userId);

    List<UserInstitution> findByInstitutionId(UUID institutionId);

    Optional<UserInstitution> findByUserIdAndInstitutionId(UUID userId, UUID institutionId);

    boolean existsByUserIdAndInstitutionId(UUID userId, UUID institutionId);

    List<UserInstitution> findByUserEmailAndStatus(String userEmail, MembershipStatus status);

    boolean existsByUserEmailAndInstitutionId(String userEmail, UUID institutionId);
}
