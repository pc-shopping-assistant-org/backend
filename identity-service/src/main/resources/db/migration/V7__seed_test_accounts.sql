-- Test accounts for trying out the admin and employee areas.
-- admin.test@pcshop.local / Admin123, employee.test@pcshop.local / Employee123.
-- Known passwords: change or remove these accounts before any real deployment.
INSERT INTO accounts (id, email, phone, password_hash, role_id, status)
SELECT '00000000-0000-0000-0000-0000000000b1',
       'admin.test@pcshop.local',
       '0900000101',
       '$2b$10$2UzyrNIQZx3/AZn1U5UKF.FvEpuPcWhnO2MVL5N4CST0VCoJ52jIS',
       id,
       'ACTIVE'
FROM roles
WHERE name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO admins (account_id, first_name, last_name, gender)
SELECT '00000000-0000-0000-0000-0000000000b1', 'Test', 'Admin', 'MALE'
WHERE EXISTS (SELECT 1 FROM accounts WHERE id = '00000000-0000-0000-0000-0000000000b1')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, email, phone, password_hash, role_id, status)
SELECT '00000000-0000-0000-0000-0000000000b2',
       'employee.test@pcshop.local',
       '0900000102',
       '$2b$10$6tjfU8yBlm8.5z0e7LPlQejQi3WoZz34Og5vlKyAQEtj6fogNUPOm',
       id,
       'ACTIVE'
FROM roles
WHERE name = 'ROLE_EMPLOYEE'
ON CONFLICT DO NOTHING;

INSERT INTO employees (account_id, first_name, last_name, gender)
SELECT '00000000-0000-0000-0000-0000000000b2', 'Test', 'Employee', 'MALE'
WHERE EXISTS (SELECT 1 FROM accounts WHERE id = '00000000-0000-0000-0000-0000000000b2')
ON CONFLICT DO NOTHING;
