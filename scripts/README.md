# Production seed

Seed chạy bằng Java trong bootstrap của `catalog-service`, không cần Node/npm.
Mặc định CLI/Compose gộp ba dataset: `seed/catalog_seed.json`,
`seed/pc_catalog_seed.json` và `seed/gear_catalog_seed.json`. Có thể thay bằng
`SEED_FILES` (danh sách path/resource phân tách bằng dấu phẩy).

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
thái cũ được giữ. Biến thể mới trong JSON là `ACTIVE` với tồn kho `0`: sản phẩm
hiện công khai là “Hết hàng” và không thể mua. Seed không đổi trạng thái biến thể
đã tồn tại. Với DB đã seed từ bản JSON cũ, chạy đúng một lần
`scripts/publish-seeded-catalog.sql` trên `catalog_db`:

```sh
psql "$CATALOG_DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/publish-seeded-catalog.sql
```

Script chỉ công bố 2.389 biến thể của tài khoản seed và có thể chạy lại mà
không ghi đè trạng thái đã công bố. Sau đó gọi
`POST /sync-service/products/sync` bằng token ADMIN để rebuild Elasticsearch.

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
| `SEED_FILES` | Danh sách JSON/resource gộp theo ID; Compose mặc định dùng components + PC + gear |
| `SEED_FILE` | Dataset đơn lẻ tương thích ngược; chỉ dùng khi `SEED_FILES` không có |
| `SYNC_URL`, `SYNC_TOKEN` | Tùy chọn POST rebuild search sau commit; token ADMIN |

Bucket và credentials phải tồn tại trước seed. Cần quyền kiểm tra bucket, đọc/ghi
object; public-read bucket/object phục vụ URL ảnh. CLI không tạo bucket hoặc policy.
`files.storage_provider=S3`, key dạng `seed/catalog/<file-UUID>/<original-name>`,
URL dạng `<public-origin>/<bucket>/<key>`. Dung lượng/MIME lấy từ object thật.

Ảnh upload dở được giữ và tái sử dụng qua HEAD khi chạy lại; không ghi đè object.
URL nguồn không còn trả ảnh (404/HTML/timeout) được ghi trong log rồi bỏ qua cùng
liên kết ảnh đó; sản phẩm vẫn được seed. Chạy lại sẽ thử ảnh còn thiếu. Lỗi S3
hoặc database vẫn làm seed thất bại. Ảnh JPEG/PNG/WebP có MIME nguồn sai được
nhận diện từ bytes trước khi upload.
Metadata `EXTERNAL`/Cloudinary đã tồn tại cũng được giữ nguyên, không tự migrate.
Ba DB có transaction riêng: validation/S3 lỗi rollback seed rows chưa commit;
lỗi giữa các lần commit được báo rõ DB đã commit để chạy lại an toàn. Flyway
migrations vẫn commit độc lập. Search lỗi làm CLI exit lỗi nhưng không rollback
data đã commit; chỉ chạy lại sync nếu cần.

Lần đầu 14.600 file nguồn/17.237 gallery records có thể vượt thời gian CD;
chạy seed thủ công trước release đầu.
Không ghi credentials vào JSON/JAR/Git. Spring/Maven không tự đọc `.env.docker`;
Compose wrapper của infra inject env khi chạy image.
