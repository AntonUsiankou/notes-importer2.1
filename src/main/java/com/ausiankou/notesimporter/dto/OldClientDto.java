package com.ausiankou.notesimporter.dto;

import lombok.Data;

@Data
public class OldClientDto {
    private String agency;
    private String guid;
    private String firstName;
    private String lastName;
    private String status;
    private String dob;
    private String createdDateTime;
}
