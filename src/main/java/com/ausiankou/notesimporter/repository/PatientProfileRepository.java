package com.ausiankou.notesimporter.repository;

import com.ausiankou.notesimporter.entity.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {
    @Query(value = "SELECT * FROM patient_profile p WHERE :guid = ANY(STRING_TO_ARRAY(p.old_client_guid, ','))",
            nativeQuery = true)
    Optional<PatientProfile> findByOldClientGuid(@Param("guid") String guid);
}
