package com.ausiankou.notesimporter.dto;

import lombok.Data;

@Data
public class OldNoteDto {
    private String comments;
    private String guid;
    private String modifiedDateTime;
    private String clientGuid;
    private String datetime;
    private String loggedUser;
    private String createdDateTime;
}
