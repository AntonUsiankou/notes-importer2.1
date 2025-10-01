package com.ausiankou.notesimporter.controller;

import com.ausiankou.notesimporter.service.NoteImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestImportController {
    private final NoteImportService noteImportService;

    @GetMapping("/run-import")
    public String runImportManually() {
        noteImportService.importNotes();
        return "Import completed manually";
    }
}
