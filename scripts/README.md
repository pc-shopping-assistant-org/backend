# Scripts

## `import-catalog-seed.js`

Imports `seed/catalog_seed.json` (brands, products, variants, images, media files and the attribute template) into the
local databases.

### Requirements

- Node.js 18+ (uses the built-in `fetch`).
- PostgreSQL running with the service databases created (`docker compose up -d postgres`) and the Flyway migrations
  of `identity-service`, `media-service` and `catalog-service` already applied, i.e. start those services once.
  `catalog-service` must be at **V11** or later (the template uses the `LIST` attribute type).
- Install the dependency once:

```bash
cd scripts
npm install
```

### Run

Run from the `backend` directory. The default mode is a **dry-run**: everything runs, then every transaction is rolled
back, so nothing is written.

```bash
node scripts/import-catalog-seed.js            # dry-run
node scripts/import-catalog-seed.js --apply    # commit
```

Each database (`identity_db`, `media_db`, `catalog_db`) has its own connection and transaction.

### Configuration

Environment variables, defaults match `docker-compose.yml`:

| Variable | Default | Meaning |
|---|---|---|
| `POSTGRES_HOST` | `localhost` | |
| `POSTGRES_PORT` | `5433` | The host port docker-compose publishes. Use `5432` for a Postgres on its default port. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `postgres` / `postgres` | |
| `IDENTITY_DB`, `MEDIA_DB`, `CATALOG_DB` | `identity_db`, `media_db`, `catalog_db` | |
| `SYNC_URL` | unset | With `--apply`, `POST`s it to rebuild the search index, e.g. `http://localhost:8080/sync-service/products/sync`. |
| `SYNC_TOKEN` | unset | Access token of an ADMIN account, sent as `Authorization: Bearer ...` to `SYNC_URL`. |

Example on Postgres port 5432 with the search index rebuilt afterwards:

```bash
POSTGRES_PORT=5432 SYNC_URL=http://localhost:8080/sync-service/products/sync SYNC_TOKEN=<admin access token> \
  node scripts/import-catalog-seed.js --apply
```

Without `SYNC_URL` the script only reminds you to call `POST /products/sync` of `sync-service` yourself.

### What it does, in order

1. **identity_db**: makes sure the employee used as `created_by` (`catalog.seed@pcshop.local` unless the account id
   already exists) exists. A passwordless account is created when missing; an existing account with another role stops
   the import.
2. **media_db**: inserts the files.
3. **catalog_db**, in one transaction:
   - Hardware categories: runs `hardware_categories.sql` if `storage` is missing, then maps seed categories by
     `seo_name`. `ssd` and `hdd` are created under `storage`.
   - Attribute template: `attribute_definitions` matched by `key`, `category_attributes` by (category `seo_name`,
     attribute `key`). A definition whose data type differs from the seed stops the import.
   - Brands, products, variants (set `ACTIVE`, quantity random 10 to 50) and images.
   - **Check**: every seed product is validated against the template the way `ProductSpecificationValidator` does it
     (inherited attributes from the parent category first, required keys, types, `ENUM`/`LIST` allowed values, unknown
     keys). Any problem stops the import and rolls back, with the problems grouped by category. This runs in dry-run too.
4. **search**: `POST` to `SYNC_URL` (with `--apply` only).

Notes:

- The script is idempotent: rows that already exist (same id, `key` or unique name) are skipped, so a second run writes
  nothing. Existing rows are never updated; to change data, edit the database or wipe it first.
- The `brand` key of `specifications` is not stored: the brand lives in `products.brand_id`.
- Attribute `required` flags, allowed values and display names live in the seed arrays `attribute_definitions` and
  `category_attributes`, so editing the template means editing `seed/catalog_seed.json`.

### Seed file format

`seed/catalog_seed.json` uses CRLF line endings and 2-space indentation. Keep both when rewriting it with a script, or
the diff will touch every line.

| Array | Purpose |
|---|---|
| `files`, `brands`, `categories`, `products`, `product_variants`, `product_images` | The catalog itself, ids kept as they are. |
| `attribute_definitions` | `key`, `display_name`, `data_type` (`NUMBER`, `STRING`, `ENUM`, `BOOLEAN`, `LIST`), `unit`, `allowed_values`, `filterable`, `comparable`. |
| `category_attributes` | `category_seo_name`, `attribute_key`, `required`, `display_order`. Attributes shared by child categories sit on the parent (`storage` for `ssd` and `hdd`). |
