package com.ausiankou.notesimporter.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "patient_profile")
@Data
public class PatientProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;

    @Column(name = "old_client_guid")
    private String oldClientGuids; // Список GUID через запятую

    @Column(name = "status_id")
    private Short statusId;
}
