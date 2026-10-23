# Scripts

## `import-catalog-seed.js`

Nhập `seed/catalog_seed.json` (thương hiệu, sản phẩm, biến thể, ảnh, file media và template thuộc tính) vào các
database local.

### Yêu cầu

- Node.js 18 trở lên (dùng `fetch` có sẵn).
- PostgreSQL đang chạy, đã tạo các database của service (`docker compose up -d postgres`) và đã áp dụng migration Flyway
  của `identity-service`, `media-service` và `catalog-service`, tức là phải khởi động các service này ít nhất một lần.
  `catalog-service` phải ở **V11** trở lên (template dùng kiểu thuộc tính `LIST`).
- Cài thư viện một lần:

```bash
cd scripts
npm install
```

### Cách chạy

Chạy từ thư mục `backend`. Chế độ mặc định là **dry-run**: chạy toàn bộ rồi rollback mọi transaction, không ghi gì.

```bash
node scripts/import-catalog-seed.js            # dry-run
node scripts/import-catalog-seed.js --apply    # ghi thật (commit)
```

Mỗi database (`identity_db`, `media_db`, `catalog_db`) có kết nối và transaction riêng.

### Cấu hình

Biến môi trường, giá trị mặc định khớp `docker-compose.yml`:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `POSTGRES_HOST` | `localhost` | |
| `POSTGRES_PORT` | `5433` | Cổng host mà docker-compose mở. Dùng `5432` nếu Postgres chạy ở cổng mặc định. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `postgres` / `postgres` | |
| `IDENTITY_DB`, `MEDIA_DB`, `CATALOG_DB` | `identity_db`, `media_db`, `catalog_db` | |
| `SYNC_URL` | không đặt | Khi có `--apply`, script gọi `POST` tới URL này để dựng lại index search, ví dụ `http://localhost:8080/sync-service/products/sync`. |
| `SYNC_TOKEN` | không đặt | Access token của tài khoản ADMIN, gửi dạng `Authorization: Bearer ...` tới `SYNC_URL`. |

Ví dụ với Postgres ở cổng 5432 và dựng lại index search sau khi nhập:

```bash
POSTGRES_PORT=5432 SYNC_URL=http://localhost:8080/sync-service/products/sync SYNC_TOKEN=<access token admin> \
  node scripts/import-catalog-seed.js --apply
```

Nếu không đặt `SYNC_URL`, script chỉ nhắc bạn tự gọi `POST /products/sync` của `sync-service`.

### Script làm gì, theo thứ tự

1. **identity_db**: đảm bảo có nhân viên dùng làm `created_by`. Nếu chưa có thì tạo tài khoản không mật khẩu
   (`catalog.seed@pcshop.local`); nếu id đó đã có nhưng role khác thì dừng, không tự sửa.
2. **media_db**: nhập các file.
3. **catalog_db**, trong một transaction:
   - Danh mục phần cứng: chạy `hardware_categories.sql` nếu thiếu `storage`, rồi ánh xạ danh mục trong seed theo
     `seo_name`. `ssd` và `hdd` được tạo làm con của `storage`.
   - Template thuộc tính: `attribute_definitions` khớp theo `key`, `category_attributes` khớp theo (`seo_name` của
     danh mục, `key` của thuộc tính). Nếu một định nghĩa có kiểu dữ liệu khác với seed thì dừng.
   - Thương hiệu, sản phẩm, biến thể (đặt `ACTIVE`, số lượng ngẫu nhiên từ 10 đến 50) và ảnh.
   - **Kiểm tra**: mỗi sản phẩm trong seed được kiểm tra với template giống cách `ProductSpecificationValidator` làm
     (thuộc tính kế thừa từ danh mục cha trước, key bắt buộc, kiểu dữ liệu, giá trị cho phép của `ENUM`/`LIST`, key
     lạ). Có lỗi là dừng và rollback, báo lỗi gom theo danh mục. Bước này chạy cả ở dry-run.
4. **search**: gọi `POST` tới `SYNC_URL` (chỉ khi có `--apply`).

Lưu ý:

- Script chạy lại được nhiều lần: dòng nào đã có (cùng id, `key` hoặc tên unique) sẽ bị bỏ qua, nên lần chạy thứ hai
  không ghi gì. Script không bao giờ cập nhật dòng đã có; muốn đổi dữ liệu thì sửa trực tiếp DB hoặc xóa trước.
- Key `brand` trong `specifications` không được lưu: thương hiệu nằm ở `products.brand_id`.
- Cờ `required`, giá trị cho phép và tên hiển thị của thuộc tính nằm trong hai mảng `attribute_definitions` và
  `category_attributes` của seed, nên muốn sửa template thì sửa `seed/catalog_seed.json`.

### Định dạng file seed

`seed/catalog_seed.json` dùng xuống dòng CRLF và thụt lề 2 dấu cách. Khi ghi lại file bằng script phải giữ cả hai,
nếu không diff sẽ đổi toàn bộ các dòng.

| Mảng | Mục đích |
|---|---|
| `files`, `brands`, `categories`, `products`, `product_variants`, `product_images` | Bản thân catalog, giữ nguyên id. |
| `attribute_definitions` | `key`, `display_name`, `data_type` (`NUMBER`, `STRING`, `ENUM`, `BOOLEAN`, `LIST`), `unit`, `allowed_values`, `filterable`, `comparable`. |
| `category_attributes` | `category_seo_name`, `attribute_key`, `required`, `display_order`. Thuộc tính chung của các danh mục con nằm ở danh mục cha (`storage` cho `ssd` và `hdd`). |
