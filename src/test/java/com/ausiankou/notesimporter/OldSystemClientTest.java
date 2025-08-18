package com.ausiankou.notesimporter;

import com.ausiankou.notesimporter.dto.OldClientDto;
import com.ausiankou.notesimporter.dto.OldNoteDto;
import com.ausiankou.notesimporter.service.OldSystemClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OldSystemClientTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OldSystemClient oldSystemClient;

    @Test
    void testGetAllClients() {
        List<OldClientDto> clients = oldSystemClient.getAllClients();
        assertNotNull(clients);
        assertFalse(clients.isEmpty());
    }

    @Test
    void testGetClientNotes() {
        String testAgency = "test-agency";
        String testGuid = "test-guid";

        List<OldNoteDto> notes = oldSystemClient.getClientNotes(testAgency, testGuid);
        assertNotNull(notes);
    }
}
