package com.ecm.media.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.media.dto.response.MediaFileResponse;
import com.ecm.media.service.MediaFileService;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/files")
@RequiredArgsConstructor
public class MediaFileController {

    private final MediaFileService mediaFileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaFileResponse> uploadImage(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success("Upload image successfully", mediaFileService.uploadImage(file));
    }

    @GetMapping
    public ApiResponse<List<MediaFileResponse>> getFiles(@RequestParam @NotEmpty List<UUID> ids) {
        return ApiResponse.success("Get files successfully", mediaFileService.getActiveFiles(ids));
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaFileResponse> getFile(@PathVariable UUID id) {
        return ApiResponse.success("Get file successfully", mediaFileService.getActiveFile(id));
    }
}
