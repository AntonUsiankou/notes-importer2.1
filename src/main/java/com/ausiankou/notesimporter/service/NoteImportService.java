package com.ausiankou.notesimporter.service;

import com.ausiankou.notesimporter.dto.OldClientDto;
import com.ausiankou.notesimporter.dto.OldNoteDto;
import com.ausiankou.notesimporter.entity.CompanyUser;
import com.ausiankou.notesimporter.entity.PatientNote;
import com.ausiankou.notesimporter.entity.PatientProfile;
import com.ausiankou.notesimporter.repository.CompanyUserRepository;
import com.ausiankou.notesimporter.repository.PatientNoteRepository;
import com.ausiankou.notesimporter.repository.PatientProfileRepository;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteImportService {
    private final OldSystemClient oldSystemClient;
    private final PatientProfileRepository patientProfileRepository;
    private final CompanyUserRepository companyUserRepository;
    private final PatientNoteRepository patientNoteRepository;


    @Transactional
    public void importNotes() {
        log.info("<-------------!------------->");
        log.info("Starting notes import process");

        List<OldClientDto> oldClients = oldSystemClient.getAllClients();
        if (oldClients.isEmpty()) {
            log.warn("No clients received from old system, aborting import");
            return;
        }
        log.info("Fetched {} clients from old system", oldClients.size());

        ImportStats stats = new ImportStats();

        for (OldClientDto oldClient : oldClients) {
            try {
                processClient(oldClient, stats);
            } catch (Exception e) {
                log.error("Error processing client with guid {}: {}", oldClient.getGuid(), e.getMessage());
                stats.incrementErrors();
            }
        }

        log.info("Import completed. Stats - Imported: {}, Updated: {}, Skipped: {}, Errors: {}",
                stats.getImported(), stats.getUpdated(), stats.getSkipped(), stats.getErrors());
    }

    private void processClient(OldClientDto oldClient, ImportStats stats) {
        log.debug("Processing client: {}", oldClient.getGuid());

        Optional<PatientProfile> patientOpt = patientProfileRepository.findByOldClientGuid(oldClient.getGuid());
        if (patientOpt.isEmpty()) {
            log.warn("No patient found for old client guid: {}", oldClient.getGuid());
            stats.incrementSkipped();
            return;
        }

        PatientProfile patient = patientOpt.get();
        log.debug("Found patient: id={}, status={}", patient.getId(), patient.getStatusId());

        if (!isPatientActive(patient)) {
            log.warn("Patient {} is not active (status ID: {})", patient.getId(), patient.getStatusId());
            stats.incrementSkipped();
            return;
        }

        List<OldNoteDto> oldNotes = oldSystemClient.getClientNotes(
                oldClient.getAgency(),
                oldClient.getGuid()
        );

        if (oldNotes.isEmpty()) {
            log.info("No notes found for client {}", oldClient.getGuid());
            return;
        }

        log.info("Processing {} notes for patient {}", oldNotes.size(), patient.getId());
        for (OldNoteDto oldNote : oldNotes) {
            try {
                processNote(oldNote, patient, stats);
            } catch (Exception e) {
                log.error("Error processing note {}: {}", oldNote.getGuid(), e.getMessage());
                stats.incrementErrors();
            }
        }
    }

    private boolean isPatientActive(PatientProfile patient) {
        final List<Integer> ACTIVE_STATUSES = List.of(200, 210, 230);
        // Явно преобразуем Short в int
        int statusId = patient.getStatusId().intValue();
        boolean isActive = ACTIVE_STATUSES.contains(statusId);

        log.debug("Patient status check: id={}, status={}, isActive={}",
                patient.getId(), statusId, isActive);

        if (statusId == 200 && !isActive) {
            log.error("INCONSISTENCY: Status 200 should be active but was not recognized!");
        }

        if (statusId == 210 && !isActive) {
            log.error("INCONSISTENCY: Status 210 should be active but was not recognized!");
        }

        if (statusId == 230 && !isActive) {
            log.error("INCONSISTENCY: Status 230 should be active but was not recognized!");
        }

        return isActive;
    }


    private void processNote(OldNoteDto oldNote, PatientProfile patient, ImportStats stats) {
        CompanyUser user = getOrCreateUser(oldNote.getLoggedUser());
        Optional<PatientNote> existingNoteOpt = patientNoteRepository.findByOldNoteGuid(oldNote.getGuid());

        if (existingNoteOpt.isPresent()) {
            updateNote(existingNoteOpt.get(), oldNote, user, stats);
        } else {
            createNote(oldNote, patient, user, stats);
        }
    }

    private void updateNote(PatientNote existingNote, OldNoteDto oldNote, CompanyUser user, ImportStats stats) {
        LocalDateTime oldNoteModified = parseDateTime(oldNote.getModifiedDateTime());
        LocalDateTime existingNoteModified = existingNote.getLastModifiedDateTime();

        if (oldNoteModified.isAfter(existingNoteModified)) {
            existingNote.setNote(oldNote.getComments());
            existingNote.setLastModifiedDateTime(oldNoteModified);
            existingNote.setLastModifiedByUser(user);
            patientNoteRepository.save(existingNote);
            stats.incrementUpdated();
            log.info("Updated note: {}", oldNote.getGuid());
        } else if (existingNoteModified.isAfter(oldNoteModified)) {
            stats.incrementSkipped();
            log.info("Skipped note (newer version exists): {}", oldNote.getGuid());
        } else {
            stats.incrementSkipped();
            log.debug("Skipped note (no changes): {}", oldNote.getGuid());
        }
    }

    private void createNote(OldNoteDto oldNote, PatientProfile patient, CompanyUser user, ImportStats stats) {
        PatientNote newNote = new PatientNote();
        newNote.setPatient(patient);
        newNote.setCreatedByUser(user);
        newNote.setLastModifiedByUser(user);
        newNote.setCreatedDateTime(parseDateTime(oldNote.getCreatedDateTime()));
        newNote.setLastModifiedDateTime(parseDateTime(oldNote.getModifiedDateTime()));
        newNote.setNote(oldNote.getComments());
        newNote.setOldNoteGuid(oldNote.getGuid());

        patientNoteRepository.save(newNote);
        stats.incrementImported();
        log.info("Created new note: {}", oldNote.getGuid());
    }

    private CompanyUser getOrCreateUser(String login) {
        return companyUserRepository.findByLogin(login)
                .orElseGet(() -> {
                    log.info("Creating new user: {}", login);
                    CompanyUser newUser = new CompanyUser();
                    newUser.setLogin(login);
                    return companyUserRepository.save(newUser);
                });
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            // Пробуем оба формата
            try {
                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException e) {
                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        } catch (DateTimeParseException e) {
            log.error("Error parsing datetime '{}', using current time", dateTimeStr);
            return LocalDateTime.now();
        }
    }

    @Data
    private static class ImportStats {
        private int imported;
        private int updated;
        private int skipped;
        private int errors;

        public void incrementImported() { imported++; }
        public void incrementUpdated() { updated++; }
        public void incrementSkipped() { skipped++; }
        public void incrementErrors() { errors++; }
    }
}