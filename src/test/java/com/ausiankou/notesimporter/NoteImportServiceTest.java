package com.ausiankou.notesimporter;

import com.ausiankou.notesimporter.dto.OldClientDto;
import com.ausiankou.notesimporter.dto.OldNoteDto;
import com.ausiankou.notesimporter.entity.CompanyUser;
import com.ausiankou.notesimporter.entity.PatientNote;
import com.ausiankou.notesimporter.entity.PatientProfile;
import com.ausiankou.notesimporter.repository.CompanyUserRepository;
import com.ausiankou.notesimporter.repository.PatientNoteRepository;
import com.ausiankou.notesimporter.repository.PatientProfileRepository;
import com.ausiankou.notesimporter.service.NoteImportService;
import com.ausiankou.notesimporter.service.OldSystemClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class NoteImportServiceTest {

    @Mock
    private OldSystemClient oldSystemClient;

    @Mock
    private PatientProfileRepository patientProfileRepository;

    @Mock
    private CompanyUserRepository companyUserRepository;

    @Mock
    private PatientNoteRepository patientNoteRepository;

    @InjectMocks
    private NoteImportService noteImportService;

    @Test
    void testImportNotesWithNewNote() {
        // Подготовка тестовых данных
        OldClientDto client = new OldClientDto();
        client.setGuid("client-guid");
        client.setAgency("test-agency");

        OldNoteDto note = new OldNoteDto();
        note.setGuid("note-guid");
        note.setComments("Test note");
        note.setLoggedUser("test-user");
        note.setCreatedDateTime("2023-01-01 12:00:00");
        note.setModifiedDateTime("2023-01-01 12:00:00");

        PatientProfile patient = new PatientProfile();
        patient.setStatusId((short)200);

        // Настройка моков
        when(oldSystemClient.getAllClients()).thenReturn(List.of(client));
        when(patientProfileRepository.findByOldClientGuid(any())).thenReturn(Optional.of(patient));
        when(oldSystemClient.getClientNotes(any(), any())).thenReturn(List.of(note));
        when(companyUserRepository.findByLogin(any())).thenReturn(Optional.empty());
        when(companyUserRepository.save(any())).thenReturn(new CompanyUser());
        when(patientNoteRepository.findByOldNoteGuid(any())).thenReturn(Optional.empty());

        // Выполнение теста
        noteImportService.importNotes();

        // Проверки
        verify(patientNoteRepository, times(1)).save(any(PatientNote.class));
    }

    @Test
    void testImportNotesWithExistingNote() {
        // Подготовка тестовых данных
        OldClientDto client = new OldClientDto();
        client.setGuid("client-guid");
        client.setAgency("test-agency");

        OldNoteDto note = new OldNoteDto();
        note.setGuid("note-guid");
        note.setComments("Updated note");
        note.setLoggedUser("test-user");
        note.setCreatedDateTime("2023-01-01 12:00:00");
        note.setModifiedDateTime("2023-01-02 12:00:00"); // Новая дата изменения

        PatientProfile patient = new PatientProfile();
        patient.setStatusId((short)200);

        PatientNote existingNote = new PatientNote();
        existingNote.setLastModifiedDateTime(LocalDateTime.of(2023, 1, 1, 12, 0));

        // Настройка моков
        when(oldSystemClient.getAllClients()).thenReturn(List.of(client));
        when(patientProfileRepository.findByOldClientGuid(any())).thenReturn(Optional.of(patient));
        when(oldSystemClient.getClientNotes(any(), any())).thenReturn(List.of(note));
        when(companyUserRepository.findByLogin(any())).thenReturn(Optional.of(new CompanyUser()));
        when(patientNoteRepository.findByOldNoteGuid(any())).thenReturn(Optional.of(existingNote));

        // Выполнение теста
        noteImportService.importNotes();

        // Проверки
        verify(patientNoteRepository, times(1)).save(existingNote);
        assertEquals("Updated note", existingNote.getNote());
    }

    @Test
    void testImportNotesWithInactivePatient() {
        OldClientDto client = new OldClientDto();
        client.setGuid("client-guid");
        client.setAgency("test-agency");

        PatientProfile patient = new PatientProfile();
        patient.setStatusId((short)100); // Неактивный статус

        when(oldSystemClient.getAllClients()).thenReturn(List.of(client));
        when(patientProfileRepository.findByOldClientGuid(any())).thenReturn(Optional.of(patient));

        noteImportService.importNotes();

        verify(oldSystemClient, never()).getClientNotes(any(), any());
    }
}