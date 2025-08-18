package com.ausiankou.notesimporter.service;

import com.ausiankou.notesimporter.dto.OldClientDto;
import com.ausiankou.notesimporter.dto.OldNoteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OldSystemClient {
    @Value("${old.system.api.base-url}")
    private String oldSystemBaseUrl;

    private final RestTemplate restTemplate;

    public List<OldClientDto> getAllClients() {
        try {
            String url = oldSystemBaseUrl + "/clients";
            OldClientDto[] clients = restTemplate.postForObject(url, null, OldClientDto[].class);
            return clients != null ? Arrays.asList(clients) : Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching clients from old system", e);
            return Collections.emptyList();
        }
    }

    public List<OldNoteDto> getClientNotes(String agency, String clientGuid) {
        try {
            String url = oldSystemBaseUrl + "/notes";
            Map<String, String> request = Map.of(
                    "agency", agency,
                    "clientGuid", clientGuid,
                    "dateFrom", "2000-01-01",
                    "dateTo", "2030-12-31"
            );
            OldNoteDto[] notes = restTemplate.postForObject(url, request, OldNoteDto[].class);
            return notes != null ? Arrays.asList(notes) : Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching notes for client {}", clientGuid, e);
            return Collections.emptyList();
        }
    }
}