package com.ecm.identity.client;

import com.ecm.common.response.ApiResponse;
import com.ecm.identity.dto.response.MediaFileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "media-service")
public interface MediaServiceClient {

    @GetMapping("/files")
    ApiResponse<List<MediaFileResponse>> getFiles(@RequestParam("ids") List<UUID> ids);
}
