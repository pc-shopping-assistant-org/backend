#!/usr/bin/env node
/**
 * Imports scripts/seed/catalog_seed.json into the local databases.
 *
 *   node scripts/import-catalog-seed.js            dry-run: runs everything, then rolls back
 *   node scripts/import-catalog-seed.js --apply    commits
 *
 * Order (decisions: tmp/catalog-seed-import-issues.md):
 *   1. identity_db  make sure the employee account used as created_by exists
 *   2. media_db     files
 *   3. catalog_db   hardware categories -> ssd/hdd under storage -> attribute template -> brands -> products -> variants -> images,
 *                   then every product is checked against the template (stops and rolls back on a mismatch)
 *   4. search       POST /products/sync (only when SYNC_URL is set, otherwise printed as a reminder)
 *
 * Each database has its own connection and transaction. Needs `pg` (npm install pg).
 *
 * Environment (defaults match docker-compose.yml):
 *   POSTGRES_HOST=localhost POSTGRES_PORT=5433 POSTGRES_USER=postgres POSTGRES_PASSWORD=postgres
 *   IDENTITY_DB=identity_db MEDIA_DB=media_db CATALOG_DB=catalog_db
 *   SYNC_URL=http://localhost:8080/sync-service/products/sync  SYNC_TOKEN=<admin JWT>
 */
const fs = require('node:fs');
const path = require('node:path');
const { randomInt } = require('node:crypto');

let Client;
try {
  ({ Client } = require('pg'));
} catch {
  console.error('Missing dependency: run `npm install pg` first.');
  process.exit(1);
}

const APPLY = process.argv.includes('--apply');
const SEED_FILE = path.join(__dirname, 'seed', 'catalog_seed.json');
const HARDWARE_CATEGORIES_SQL = path.join(__dirname, '..', 'catalog-service', 'src', 'main', 'resources', 'db', 'seed', 'prod', 'hardware_categories.sql');

const BATCH_SIZE = 500;
const VARIANT_QUANTITY_MIN = 10;
const VARIANT_QUANTITY_MAX = 50;
const STORAGE_SEO_NAME = 'storage';
const EMPLOYEE_ROLE = 'ROLE_EMPLOYEE';
const SEED_EMPLOYEE = { email: 'catalog.seed@pcshop.local', firstName: 'Catalog', lastName: 'Seed', gender: 'MALE' };
// The brand lives in the brands table (products.brand_id), so it is not stored again in specifications.
const SKIPPED_SPECIFICATION_KEYS = new Set(['brand']);

const specificationsOf = (product) =>
  Object.fromEntries(Object.entries(product.specifications).filter(([key]) => !SKIPPED_SPECIFICATION_KEYS.has(key)));

const connectionOf = (database) => ({
  host: process.env.POSTGRES_HOST || 'localhost',
  port: Number(process.env.POSTGRES_PORT || 5433),
  user: process.env.POSTGRES_USER || 'postgres',
  password: process.env.POSTGRES_PASSWORD || 'postgres',
  database,
});

/** Runs `work` in one transaction on its own connection; commits with --apply, rolls back otherwise. */
async function inTransaction(database, work) {
  const client = new Client(connectionOf(database));
  await client.connect();
  try {
    await client.query('BEGIN');
    const result = await work(client);
    await client.query(APPLY ? 'COMMIT' : 'ROLLBACK');
    return result;
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    throw error;
  } finally {
    await client.end();
  }
}

/** Multi-row INSERT in batches; rows that conflict are skipped. Returns the number of rows actually inserted. */
async function batchInsert(client, table, columns, rows, jsonbColumns = []) {
  let inserted = 0;
  for (let start = 0; start < rows.length; start += BATCH_SIZE) {
    const batch = rows.slice(start, start + BATCH_SIZE);
    const params = [];
    const tuples = batch.map((row) => {
      const placeholders = columns.map((column, index) => {
        params.push(row[index]);
        return `$${params.length}${jsonbColumns.includes(column) ? '::jsonb' : ''}`;
      });
      return `(${placeholders.join(', ')})`;
    });
    const result = await client.query(
      `INSERT INTO ${table} (${columns.join(', ')}) VALUES ${tuples.join(', ')} ON CONFLICT DO NOTHING`,
      params,
    );
    inserted += result.rowCount;
  }
  return inserted;
}

/** Every id of the seed must be in the table afterwards; a skipped row (unique name/slug clash) would break the FKs below. */
async function assertAllPresent(client, table, ids) {
  const { rows } = await client.query(`SELECT id FROM ${table} WHERE id = ANY($1::uuid[])`, [ids]);
  const present = new Set(rows.map((row) => row.id));
  const missing = ids.filter((id) => !present.has(id));
  if (missing.length > 0) {
    throw new Error(`${table}: ${missing.length} seed row(s) were skipped by a conflict, e.g. ${missing[0]}`);
  }
}

// ---------------------------------------------------------------- identity_db
async function ensureSeedEmployee(createdBy) {
  return inTransaction(process.env.IDENTITY_DB || 'identity_db', async (client) => {
    const existing = await client.query(
      `SELECT r.name AS role FROM accounts a JOIN roles r ON r.id = a.role_id WHERE a.id = $1`,
      [createdBy],
    );
    if (existing.rowCount > 0) {
      if (existing.rows[0].role !== EMPLOYEE_ROLE) {
        throw new Error(`Account ${createdBy} exists with role ${existing.rows[0].role}, expected ${EMPLOYEE_ROLE}`);
      }
      return 'already exists';
    }
    const role = await client.query(`SELECT id FROM roles WHERE name = $1`, [EMPLOYEE_ROLE]);
    if (role.rowCount === 0) {
      throw new Error(`Role ${EMPLOYEE_ROLE} not found in identity_db`);
    }
    // No password: this account exists only so created_by points at a real employee, it cannot log in.
    await client.query(
      `INSERT INTO accounts (id, email, role_id, status) VALUES ($1, $2, $3, 'ACTIVE')`,
      [createdBy, SEED_EMPLOYEE.email, role.rows[0].id],
    );
    await client.query(
      `INSERT INTO employees (account_id, first_name, last_name, gender) VALUES ($1, $2, $3, $4)`,
      [createdBy, SEED_EMPLOYEE.firstName, SEED_EMPLOYEE.lastName, SEED_EMPLOYEE.gender],
    );
    return 'created';
  });
}

// ------------------------------------------------------------------- media_db
async function importFiles(files) {
  return inTransaction(process.env.MEDIA_DB || 'media_db', async (client) => {
    const inserted = await batchInsert(
      client,
      'files',
      ['id', 'storage_provider', 'storage_key', 'original_name', 'mime_type', 'size_bytes', 'public_url', 'status'],
      files.map((f) => [f.id, f.storage_provider, f.storage_key, f.original_name, f.mime_type, f.size_bytes, f.public_url, f.status]),
    );
    await assertAllPresent(client, 'files', files.map((f) => f.id));
    return inserted;
  });
}

// ----------------------------------------------------------------- catalog_db
/** seo_name -> id of the active categories. */
async function loadCategoryIds(client) {
  const { rows } = await client.query(`SELECT id, seo_name FROM categories WHERE status <> 'DELETED'`);
  return new Map(rows.map((row) => [row.seo_name, row.id]));
}

/**
 * The hardware categories of hardware_categories.sql are the reference. They are created from that script when
 * missing; every category of the seed is then mapped to one of them by seo_name. A seed category the script does not
 * know (ssd, hdd) is created under storage.
 */
async function resolveCategories(client, seedCategories) {
  let ids = await loadCategoryIds(client);
  let ranHardwareScript = false;
  if (!ids.has(STORAGE_SEO_NAME)) {
    await client.query(fs.readFileSync(HARDWARE_CATEGORIES_SQL, 'utf8'));
    ranHardwareScript = true;
    ids = await loadCategoryIds(client);
  }

  const storageId = ids.get(STORAGE_SEO_NAME);
  const categoryIdOf = new Map(); // seed category id -> category id in the database
  const created = [];
  for (const category of seedCategories) {
    if (ids.has(category.seo_name)) {
      categoryIdOf.set(category.id, ids.get(category.seo_name));
      continue;
    }
    if (category.hardware_type !== 'STORAGE' || !storageId) {
      throw new Error(`Category "${category.seo_name}" is unknown and cannot be placed under ${STORAGE_SEO_NAME}`);
    }
    await client.query(
      `INSERT INTO categories (id, name, seo_name, description, parent_id, status, hardware_type)
       VALUES ($1, $2, $3, $4, $5, $6, $7)`,
      [category.id, category.name, category.seo_name, category.description, storageId, category.status, category.hardware_type],
    );
    categoryIdOf.set(category.id, category.id);
    created.push(category.seo_name);
  }
  return { categoryIdOf, created, ranHardwareScript };
}

const jsonOrNull = (value) => (value == null ? null : JSON.stringify(value));

/**
 * The attribute template: definitions are matched by key, links by (category seo_name, attribute key), because the
 * ids of the categories differ between environments. Rows that already exist are kept as they are; a definition whose
 * data type differs from the seed stops the import, since stored specifications are typed by it.
 */
async function importAttributeTemplate(client, data, categoryIds) {
  const definitions = data.attribute_definitions ?? [];
  const links = data.category_attributes ?? [];

  const report = {};
  report.definitions = await batchInsert(
    client,
    'attribute_definitions',
    ['key', 'display_name', 'data_type', 'unit', 'allowed_values', 'filterable', 'comparable'],
    definitions.map((d) => [d.key, d.display_name, d.data_type, d.unit, jsonOrNull(d.allowed_values), d.filterable, d.comparable]),
    ['allowed_values'],
  );
  const { rows } = await client.query('SELECT id, key, data_type FROM attribute_definitions WHERE key = ANY($1::text[])', [definitions.map((d) => d.key)]);
  const stored = new Map(rows.map((row) => [row.key, row]));
  for (const d of definitions) {
    if (!stored.has(d.key)) throw new Error(`attribute_definitions: "${d.key}" is missing after the insert`);
    if (stored.get(d.key).data_type !== d.data_type) {
      throw new Error(`attribute_definitions: "${d.key}" is ${stored.get(d.key).data_type} in the database but ${d.data_type} in the seed`);
    }
  }

  report.links = await batchInsert(
    client,
    'category_attributes',
    ['category_id', 'attribute_id', 'required', 'display_order'],
    links.map((link) => {
      const categoryId = categoryIds.get(link.category_seo_name);
      const attribute = stored.get(link.attribute_key);
      if (!categoryId) throw new Error(`category_attributes: unknown category "${link.category_seo_name}"`);
      if (!attribute) throw new Error(`category_attributes: unknown attribute "${link.attribute_key}"`);
      return [categoryId, attribute.id, link.required, link.display_order];
    }),
  );
  return report;
}

/**
 * Mirrors ProductSpecificationValidator on every seed product: the template of a category is the one of its
 * ancestors (root first) followed by its own. Returns the problems grouped by category.
 */
async function checkProductsAgainstTemplate(client, data, categoryIdOf) {
  const { rows: categoryRows } = await client.query('SELECT id, seo_name, parent_id FROM categories');
  const category = new Map(categoryRows.map((row) => [row.id, row]));
  const { rows: templateRows } = await client.query(
    `SELECT ca.category_id, ca.required, ca.display_order, d.key, d.data_type, d.allowed_values
       FROM category_attributes ca JOIN attribute_definitions d ON d.id = ca.attribute_id
      ORDER BY ca.display_order`);
  const ownAttributes = new Map();
  for (const row of templateRows) {
    if (!ownAttributes.has(row.category_id)) ownAttributes.set(row.category_id, []);
    ownAttributes.get(row.category_id).push(row);
  }
  const templateOf = (categoryId) => {
    const chain = [];
    for (let id = categoryId; id; id = category.get(id)?.parent_id) chain.unshift(id);
    const byKey = new Map();
    for (const id of chain) for (const attribute of ownAttributes.get(id) ?? []) if (!byKey.has(attribute.key)) byKey.set(attribute.key, attribute);
    return byKey;
  };
  const isBlank = (value) => value == null || (typeof value === 'string' && value.trim() === '') || (Array.isArray(value) && value.length === 0);
  const problemOf = (attribute, value) => {
    switch (attribute.data_type) {
      case 'NUMBER': return typeof value === 'number' ? null : 'must be a number';
      case 'BOOLEAN': return typeof value === 'boolean' ? null : 'must be true or false';
      case 'STRING': return typeof value === 'string' ? null : 'must be a string';
      case 'ENUM':
        return typeof value === 'string' && (attribute.allowed_values ?? []).includes(value.trim()) ? null : `must be one of ${JSON.stringify(attribute.allowed_values)}`;
      case 'LIST': {
        if (!Array.isArray(value) || value.some((item) => typeof item !== 'string' || item.trim() === '')) return 'must be a list of non-empty strings';
        const allowed = attribute.allowed_values;
        return allowed && !value.every((item) => allowed.includes(item.trim())) ? `every item must be one of ${JSON.stringify(allowed)}` : null;
      }
      default: return `unknown data type ${attribute.data_type}`;
    }
  };

  const problems = new Map(); // category seo_name -> messages
  const add = (categoryId, message) => {
    const name = category.get(categoryId)?.seo_name ?? categoryId;
    if (!problems.has(name)) problems.set(name, []);
    problems.get(name).push(message);
  };
  for (const product of data.products) {
    const categoryId = categoryIdOf.get(product.category_id);
    const template = templateOf(categoryId);
    const specifications = specificationsOf(product);
    for (const attribute of template.values()) {
      const value = specifications[attribute.key];
      if (isBlank(value)) {
        if (attribute.required) add(categoryId, `${product.name}: ${attribute.key} is required`);
        continue;
      }
      const problem = problemOf(attribute, value);
      if (problem) add(categoryId, `${product.name}: ${attribute.key} ${problem}`);
    }
    for (const key of Object.keys(specifications)) {
      if (!template.has(key)) add(categoryId, `${product.name}: ${key} is not an attribute of this category`);
    }
  }
  return problems;
}

async function importCatalog(data, createdBy) {
  return inTransaction(process.env.CATALOG_DB || 'catalog_db', async (client) => {
    const report = {};

    const categories = await resolveCategories(client, data.categories);
    report.categoriesCreated = categories.created;
    report.hardwareScriptRan = categories.ranHardwareScript;

    report.attributeTemplate = await importAttributeTemplate(client, data, await loadCategoryIds(client));

    report.brands = await batchInsert(
      client,
      'brands',
      ['id', 'name', 'seo_name', 'description', 'image_file_id', 'status'],
      data.brands.map((b) => [b.id, b.name, b.seo_name, b.description, b.image_file_id, b.status]),
    );
    await assertAllPresent(client, 'brands', data.brands.map((b) => b.id));

    report.products = await batchInsert(
      client,
      'products',
      ['id', 'name', 'seo_name', 'brand_id', 'category_id', 'specifications', 'description', 'status', 'created_by'],
      data.products.map((p) => [
        p.id, p.name, p.seo_name, p.brand_id, categories.categoryIdOf.get(p.category_id),
        JSON.stringify(specificationsOf(p)), p.description, p.status, createdBy,
      ]),
      ['specifications'],
    );
    await assertAllPresent(client, 'products', data.products.map((p) => p.id));

    report.variants = await batchInsert(
      client,
      'product_variants',
      ['id', 'product_id', 'price', 'quantity', 'sku', 'model', 'description', 'warranty_months', 'barcode', 'release_at', 'status', 'created_by', 'image_file_id'],
      data.product_variants.map((v) => [
        v.id, v.product_id, v.price, randomInt(VARIANT_QUANTITY_MIN, VARIANT_QUANTITY_MAX + 1), v.sku, v.model,
        v.description, v.warranty_months, v.barcode, v.release_at, 'ACTIVE', createdBy, v.image_file_id,
      ]),
    );
    await assertAllPresent(client, 'product_variants', data.product_variants.map((v) => v.id));

    report.images = await batchInsert(
      client,
      'product_images',
      ['id', 'product_id', 'image_file_id', 'is_main'],
      data.product_images.map((i) => [i.id, i.product_id, i.image_file_id, i.is_main]),
    );
    await assertAllPresent(client, 'product_images', data.product_images.map((i) => i.id));

    const problems = await checkProductsAgainstTemplate(client, data, categories.categoryIdOf);
    if (problems.size > 0) {
      const total = [...problems.values()].reduce((sum, list) => sum + list.length, 0);
      const lines = [...problems].map(([name, list]) => `  ${name}: ${list.length} problem(s), e.g. ${list[0]}`);
      throw new Error(`${total} product specification problem(s) against the attribute template, nothing written:\n${lines.join('\n')}`);
    }
    report.templateCheck = `${data.products.length} products match the template`;

    return report;
  });
}

// ------------------------------------------------------------------- search
async function syncSearchIndex() {
  const url = process.env.SYNC_URL;
  if (!url) {
    console.log('4. search: set SYNC_URL (+ SYNC_TOKEN of an ADMIN) or call POST /products/sync of sync-service yourself.');
    return;
  }
  const response = await fetch(url, {
    method: 'POST',
    headers: process.env.SYNC_TOKEN ? { Authorization: `Bearer ${process.env.SYNC_TOKEN}` } : {},
  });
  console.log(`4. search: POST ${url} -> HTTP ${response.status}`);
}

async function main() {
  const data = JSON.parse(fs.readFileSync(SEED_FILE, 'utf8'));
  const createdByValues = [...new Set(data.products.map((p) => p.created_by))];
  if (createdByValues.length !== 1) {
    throw new Error(`Expected one created_by in the seed, found ${createdByValues.length}`);
  }
  const createdBy = createdByValues[0];

  console.log(`Mode: ${APPLY ? 'APPLY (commit)' : 'DRY-RUN (rollback)'}`);
  console.log(`Seed: ${data.files.length} files, ${data.brands.length} brands, ${data.categories.length} categories, `
    + `${data.products.length} products, ${data.product_variants.length} variants, ${data.product_images.length} images`);

  console.log(`1. identity_db: employee ${createdBy} -> ${await ensureSeedEmployee(createdBy)}`);
  console.log(`2. media_db: files inserted: ${await importFiles(data.files)}`);
  const report = await importCatalog(data, createdBy);
  console.log('3. catalog_db:', JSON.stringify(report));

  if (APPLY) {
    await syncSearchIndex();
  } else {
    console.log('Dry-run finished, nothing was written. Re-run with --apply to commit.');
  }
}

main().catch((error) => {
  console.error(`FAILED: ${error.message}`);
  process.exit(1);
});
