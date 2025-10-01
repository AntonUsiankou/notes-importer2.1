package com.ausiankou.notesimporter.service;

import com.ausiankou.notesimporter.dto.OldClientDto;
import com.ausiankou.notesimporter.dto.OldNoteDto;
import com.ausiankou.notesimporter.entity.CompanyUser;
import com.ausiankou.notesimporter.entity.PatientNote;
import com.ausiankou.notesimporter.entity.PatientProfile;
import com.ausiankou.notesimporter.repository.CompanyUserRepository;
import com.ausiankou.notesimporter.repository.PatientNoteRepository;
import com.ausiankou.notesimporter.repository.PatientProfileRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteImportService {
    private final OldSystemClient oldSystemClient;
    private final PatientProfileRepository patientProfileRepository;
    private final CompanyUserRepository companyUserRepository;
    private final PatientNoteRepository patientNoteRepository;

    // УБРАНА аннотация @Transactional с основного метода
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

        // Оптимизация: получаем всех пациентов за ОДИН запрос к БД
        Map<String, PatientProfile> patientsByGuid = getPatientsByGuid(oldClients);

        // Кэш пользователей для уменьшения запросов к БД
        Map<String, CompanyUser> userCache = new HashMap<>();

        for (OldClientDto oldClient : oldClients) {
            try {
                processClient(oldClient, patientsByGuid, userCache, stats);
            } catch (Exception e) {
                log.error("Error processing client with guid {}: {}", oldClient.getGuid(), e.getMessage());
                stats.incrementErrors();
            }
        }

        log.info("Import completed. Stats - Imported: {}, Updated: {}, Skipped: {}, Errors: {}",
                stats.getImported(), stats.getUpdated(), stats.getSkipped(), stats.getErrors());
    }

    private Map<String, PatientProfile> getPatientsByGuid(List<OldClientDto> oldClients) {
        List<String> clientGuids = oldClients.stream()
                .map(OldClientDto::getGuid)
                .collect(Collectors.toList());

        log.debug("Fetching patients for {} guids", clientGuids.size());

        // Получаем всех пациентов за ОДИН запрос
        List<PatientProfile> patients = patientProfileRepository.findByOldClientGuidsIn(clientGuids);

        Map<String, PatientProfile> result = new HashMap<>();
        for (PatientProfile patient : patients) {
            if (patient.getOldClientGuids() != null) {
                // Обрабатываем случай, когда в oldClientGuid несколько GUID через запятую
                String[] guids = patient.getOldClientGuids().split(",");
                for (String guid : guids) {
                    result.put(guid.trim(), patient);
                }
            }
        }

        log.debug("Found {} patients for {} guids", result.size(), clientGuids.size());
        return result;
    }

    private void processClient(OldClientDto oldClient, Map<String, PatientProfile> patientsByGuid,
                               Map<String, CompanyUser> userCache, ImportStats stats) {
        log.debug("Processing client: {}", oldClient.getGuid());

        PatientProfile patient = patientsByGuid.get(oldClient.getGuid());
        if (patient == null) {
            log.warn("No patient found for old client guid: {}", oldClient.getGuid());
            stats.incrementSkipped();
            return;
        }

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

        // Обрабатываем каждую заметку с отдельной транзакцией
        for (OldNoteDto oldNote : oldNotes) {
            try {
                processNoteInTransaction(oldNote, patient, userCache, stats);
            } catch (Exception e) {
                log.error("Error processing note {}: {}", oldNote.getGuid(), e.getMessage());
                stats.incrementErrors();
            }
        }
    }

    // Отдельная транзакция для каждой заметки
    @Transactional
    protected void processNoteInTransaction(OldNoteDto oldNote, PatientProfile patient,
                                            Map<String, CompanyUser> userCache, ImportStats stats) {
        processNote(oldNote, patient, userCache, stats);
    }

    private void processNote(OldNoteDto oldNote, PatientProfile patient,
                             Map<String, CompanyUser> userCache, ImportStats stats) {
        CompanyUser user = getOrCreateUser(oldNote.getLoggedUser(), userCache);
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
            log.debug("Skipped note (newer version exists): {}", oldNote.getGuid());
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

    private CompanyUser getOrCreateUser(String login, Map<String, CompanyUser> userCache) {
        // Пробуем получить из кэша
        CompanyUser cachedUser = userCache.get(login);
        if (cachedUser != null) {
            return cachedUser;
        }

        // Ищем в БД или создаем нового
        CompanyUser user = companyUserRepository.findByLogin(login)
                .orElseGet(() -> {
                    log.info("Creating new user: {}", login);
                    CompanyUser newUser = new CompanyUser();
                    newUser.setLogin(login);
                    return companyUserRepository.save(newUser);
                });

        // Сохраняем в кэш
        userCache.put(login, user);
        return user;
    }

    private boolean isPatientActive(PatientProfile patient) {
        int statusId = patient.getStatusId().intValue();
        boolean isActive = statusId == 200 || statusId == 210 || statusId == 230;

        log.debug("Patient status check: id={}, status={}, isActive={}",
                patient.getId(), statusId, isActive);

        return isActive;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
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
    public static class ImportStats {
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