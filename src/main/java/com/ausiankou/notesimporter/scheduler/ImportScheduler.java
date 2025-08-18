package com.ausiankou.notesimporter.scheduler;

import com.ausiankou.notesimporter.service.NoteImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportScheduler {
    private final NoteImportService noteImportService;

    //@Scheduled(cron = "0 15 1/2 * * *")
    @Scheduled(cron = "0 * * * * *")
    public void scheduleImport() {
        try {
            log.info("Starting scheduled import at {}", System.currentTimeMillis());
            noteImportService.importNotes();
            log.info("Scheduled import completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled import", e);
        }
    }
}
//@Scheduled(cron = "0 * * * * *")