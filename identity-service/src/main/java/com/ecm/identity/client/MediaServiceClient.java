package com.ecm.identity.client;

import com.ecm.common.response.ApiResponse;
import com.ecm.identity.dto.response.MediaFileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "media-service")
public interface MediaServiceClient {

    @GetMapping("/files")
    ApiResponse<List<MediaFileResponse>> getFiles(@RequestParam("ids") List<UUID> ids);

    /** Uploads an image on behalf of the owner of the bearer token; this service has no caller token to pass on. */
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<MediaFileResponse> upload(@RequestHeader("Authorization") String authorization,
                                          @RequestPart("file") MultipartFile file);
}
