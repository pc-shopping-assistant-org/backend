package com.ecm.catalog.bootstrap;

import com.ecm.catalog.entity.CatalogStatus;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Deterministic object keys allow retry after upload succeeds but a DB transaction rolls back. */
final class SeedImages {
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int DEFAULT_WORKERS = 8;
    private static final int MAX_WORKERS = 32;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String STORAGE_PROVIDER = "S3";
    private final SeedDatabase media;
    private final List<SeedData.Media> missing;
    final Map<UUID, UUID> ids;

    SeedImages(SeedDatabase media, List<SeedData.Media> files) throws Exception {
        this.media = media;
        var existing = media.query("SELECT id, storage_provider, storage_key FROM files");
        ids = SeedDatabase.ids(files, SeedData.Media::id, file -> STORAGE_PROVIDER + "/" + objectKey(file),
                existing, row -> row.get("storage_provider") + "/" + row.get("storage_key"));
        var storedIds = existing.stream().map(row -> row.get("id")).collect(java.util.stream.Collectors.toSet());
        missing = files.stream().filter(file -> !storedIds.contains(ids.get(file.id()))).toList();
    }

    void insert(Map<String, String> environment) throws Exception {
        if (missing.isEmpty()) {
            System.out.println("media: all seed file records already exist; no image requests");
            return;
        }
        String endpoint = required(environment, "S3_ENDPOINT");
        String bucket = environment.getOrDefault("S3_BUCKET", "pcshop-media");
        String publicBase = required(environment, "S3_PUBLIC_BASE_URL").replaceAll("/+$", "");
        URI publicUri = URI.create(publicBase);
        if (publicUri.getHost() == null || !(publicUri.getScheme().equals("https") || publicUri.getScheme().equals("http"))) {
            throw new IllegalArgumentException("S3_PUBLIC_BASE_URL must be an absolute HTTP(S) URL");
        }
        int workers = Integer.parseInt(environment.getOrDefault("SEED_IMAGE_WORKERS", String.valueOf(DEFAULT_WORKERS)));
        if (workers < 1 || workers > MAX_WORKERS) throw new IllegalArgumentException("SEED_IMAGE_WORKERS must be between 1 and " + MAX_WORKERS);
        try (var client = S3Client.builder().endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .region(Region.of(environment.getOrDefault("S3_REGION", "us-east-1")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        required(environment, "AWS_ACCESS_KEY_ID"), required(environment, "AWS_SECRET_ACCESS_KEY")))).build();
             var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(REQUEST_TIMEOUT).build();
             var executor = Executors.newFixedThreadPool(workers)) {
            client.headBucket(request -> request.bucket(bucket));
            List<Object[]> records = new ArrayList<>();
            int skipped = 0;
            for (int start = 0; start < missing.size(); start += workers) {
                var batch = missing.subList(start, Math.min(start + workers, missing.size()));
                List<Callable<Object[]>> uploads = batch.stream()
                        .<Callable<Object[]>>map(file -> () -> upload(file, http, client, bucket, publicBase)).toList();
                var futures = executor.invokeAll(uploads);
                for (int index = 0; index < futures.size(); index++) {
                    Object[] record = futures.get(index).get();
                    if (record == null) {
                        ids.remove(batch.get(index).id());
                        skipped++;
                    } else records.add(record);
                }
                if (start % 100 < workers) System.out.println("media: prepared " + (start + batch.size()) + "/" + missing.size());
            }
            int inserted = media.insert("""
                    INSERT INTO files (id, storage_provider, storage_key, original_name, mime_type, size_bytes, public_url, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, records);
            media.requireIds("files", ids.values());
            System.out.println("media: inserted " + inserted + " S3 file records; skipped " + skipped + " unavailable source images");
        }
    }

    private Object[] upload(SeedData.Media file, HttpClient http, S3Client s3, String bucket, String publicBase) throws Exception {
        String key = objectKey(file);
        HeadObjectResponse metadata;
        try {
            metadata = s3.headObject(request -> request.bucket(bucket).key(key));
        } catch (S3Exception missingObject) {
            if (missingObject.statusCode() != 404) throw missingObject;
            byte[] bytes;
            String mime;
            try {
                var response = http.send(HttpRequest.newBuilder(URI.create(file.publicUrl())).timeout(REQUEST_TIMEOUT).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                mime = response.headers().firstValue("Content-Type").orElse("").split(";")[0].trim();
                try (var input = response.body()) {
                    if (response.statusCode() != 200) {
                        throw new InvalidSourceImage("HTTP " + response.statusCode());
                    }
                    bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
                }
                if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) throw new InvalidSourceImage("invalid image size");
                if (!mime.startsWith("image/")) mime = imageMime(bytes);
                if (mime == null) throw new InvalidSourceImage("unrecognized image bytes");
            } catch (IOException | InvalidSourceImage invalid) {
                System.err.println("media: skipped source image " + file.id() + " (" + file.publicUrl() + "): " + invalid.getMessage());
                return null;
            }
            String contentType = mime;
            try {
                s3.putObject(request -> request.bucket(bucket).key(key).contentType(contentType).ifNoneMatch("*"), RequestBody.fromBytes(bytes));
            } catch (S3Exception concurrentUpload) {
                if (concurrentUpload.statusCode() != 412) throw concurrentUpload;
            }
            metadata = s3.headObject(request -> request.bucket(bucket).key(key));
        }
        if (metadata.contentLength() <= 0 || metadata.contentType() == null || !metadata.contentType().startsWith("image/")) {
            throw new IllegalStateException("Existing S3 object is not a nonempty image: " + key);
        }
        String publicUrl = publicBase + "/" + encode(bucket) + "/" + java.util.Arrays.stream(key.split("/"))
                .map(SeedImages::encode).collect(java.util.stream.Collectors.joining("/"));
        return new Object[] {ids.get(file.id()), STORAGE_PROVIDER, key, file.originalName(), metadata.contentType(),
                metadata.contentLength(), publicUrl, CatalogStatus.ACTIVE};
    }

    static String objectKey(SeedData.Media file) { return "seed/catalog/" + file.id() + "/" + file.originalName(); }
    private static final class InvalidSourceImage extends RuntimeException {
        InvalidSourceImage(String message) { super(message); }
    }
    private static String imageMime(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return null;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing seed configuration: " + key);
        return value;
    }
}
