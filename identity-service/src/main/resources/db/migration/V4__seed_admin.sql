-- Seed the first admin account so ROLE_ADMIN can log in. No use case creates the
-- first admin, so it is seeded by hand (dev-only credentials, documented in README).
INSERT INTO accounts (id, email, phone, password_hash, role_id, status)
SELECT '00000000-0000-0000-0000-000000000001',
       'admin@pcshop.local',
       '0900000001',
       '$2b$10$rhu8gWPVvE6.GQvXw55kIO3y/V9CwvxU2/M1IycpOUcFCCEDM/Ana',
       id,
       'ACTIVE'
FROM roles
WHERE name = 'ROLE_ADMIN';

INSERT INTO admins (account_id, first_name, last_name, gender)
VALUES ('00000000-0000-0000-0000-000000000001', 'System', 'Admin', 'MALE');
