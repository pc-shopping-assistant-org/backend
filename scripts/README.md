# Production seed

Seed chạy bằng Java trong bootstrap của `catalog-service`, không cần Node/npm.
Nguồn dữ liệu duy nhất: `seed/catalog_seed.json` (giữ nguyên JSON CRLF/indent khi sửa).

```sh
# Từ backend/, sau khi export runtime env vào shell:
mvn -pl catalog-service -am -Pseed-prod -DskipTests package

# Hoặc dùng JAR đã build:
java -jar catalog-service/target/catalog-service-0.0.1-SNAPSHOT.jar --seed-prod

# Trên server từ infra/ (image đã publish):
./scripts/seed-prod.sh
```

CLI chỉ INSERT bản ghi còn thiếu, bỏ qua bản ghi đã có. Nó migrate ba database,
chuẩn bị taxonomy/template và kiểm tra sản phẩm bằng validator của catalog,
tải ảnh nguồn lên S3, rồi commit identity → media → catalog. UUID được ánh xạ theo
natural key để liên kết đúng dữ liệu đã tồn tại. ID, metadata, giá, tồn kho và trạng
thái cũ được giữ. Tồn kho/trạng thái mới lấy từ JSON (hiện `0` / `INACTIVE`).

| Env | Giá trị / mục đích |
| --- | --- |
| `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_USER` | Mặc định `localhost`, `5433`, `postgres` |
| `POSTGRES_PASSWORD` | Bắt buộc, không có password mặc định |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | Ưu tiên khi chạy image; URL catalog làm base cho ba DB |
| `IDENTITY_DB`, `MEDIA_DB`, `CATALOG_DB` | Mặc định `identity_db`, `media_db`, `catalog_db` |
| `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET` | Endpoint bắt buộc khi có ảnh thiếu; region `us-east-1`, bucket `pcshop-media` |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | S3 client key tạo trong Admin UI; không phải admin UI password |
| `S3_PUBLIC_BASE_URL` | Public origin, ví dụ `https://gearpc-files.lamdx4.duckdns.org`, không thêm bucket |
| `SEED_IMAGE_WORKERS` | Tối đa số ảnh tải đồng thời, mặc định `8`, phạm vi `1..32` |
| `SEED_FILE` | JSON khác khi cần; bỏ trống dùng dataset được đóng trong JAR |
| `SYNC_URL`, `SYNC_TOKEN` | Tùy chọn POST rebuild search sau commit; token ADMIN |

Bucket và credentials phải tồn tại trước seed. Cần quyền kiểm tra bucket, đọc/ghi
object; public-read bucket/object phục vụ URL ảnh. CLI không tạo bucket hoặc policy.
`files.storage_provider=S3`, key dạng `seed/catalog/<file-UUID>/<original-name>`,
URL dạng `<public-origin>/<bucket>/<key>`. Dung lượng/MIME lấy từ object thật.

Ảnh upload dở được giữ và tái sử dụng qua HEAD khi chạy lại; không ghi đè object.
Metadata `EXTERNAL`/Cloudinary đã tồn tại cũng được giữ nguyên, không tự migrate.
Ba DB có transaction riêng: validation/upload lỗi rollback seed rows chưa commit;
lỗi giữa các lần commit được báo rõ DB đã commit để chạy lại an toàn. Flyway
migrations vẫn commit độc lập. Search lỗi làm CLI exit lỗi nhưng không rollback
data đã commit; chỉ chạy lại sync nếu cần.

Lần đầu 8.502 ảnh có thể vượt thời gian CD; chạy seed thủ công trước release đầu.
Không ghi credentials vào JSON/JAR/Git. Spring/Maven không tự đọc `.env.docker`;
Compose wrapper của infra inject env khi chạy image.
