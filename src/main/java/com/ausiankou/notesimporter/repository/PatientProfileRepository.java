package com.ausiankou.notesimporter.repository;

import com.ausiankou.notesimporter.entity.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {

    // Старый метод для одного GUID
    @Query("SELECT p FROM PatientProfile p WHERE :guid = ANY(STRING_TO_ARRAY(p.oldClientGuids, ','))")
    Optional<PatientProfile> findByOldClientGuid(@Param("guid") String guid);

    // НОВЫЙ метод для нескольких GUID
    @Query("SELECT p FROM PatientProfile p WHERE p.oldClientGuids IS NOT NULL")
    List<PatientProfile> findByOldClientGuidsIn(@Param("guids") List<String> guids);
}