package com.ausiankou.notesimporter.repository;

import com.ausiankou.notesimporter.entity.PatientNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientNoteRepository extends JpaRepository<PatientNote, Long> {
    Optional<PatientNote> findByOldNoteGuid(String oldNoteGuid);
}
