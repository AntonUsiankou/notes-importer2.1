package com.ausiankou.notesimporter.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_note")
@Data
public class PatientNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_date_time")
    private LocalDateTime createdDateTime;

    @Column(name = "last_modified_date_time")
    private LocalDateTime lastModifiedDateTime;

    @ManyToOne
    @JoinColumn(name = "created_by_user_id")
    private CompanyUser createdByUser;

    @ManyToOne
    @JoinColumn(name = "last_modified_by_user_id")
    private CompanyUser lastModifiedByUser;

    private String note;

    @ManyToOne
    @JoinColumn(name = "patient_id")
    private PatientProfile patient;

    @Column(unique = true)
    private String oldNoteGuid; // GUID из старой системы
}
