-- VNPAY replaces the placeholder online card method as the way to pay online; the old row stays for the payments made with it.
INSERT INTO payment_methods (code, name)
SELECT 'VNPAY', 'VNPAY' WHERE NOT EXISTS (SELECT 1 FROM payment_methods WHERE code = 'VNPAY');
UPDATE payment_methods SET status = 'INACTIVE' WHERE code = 'ONLINE_CARD';
