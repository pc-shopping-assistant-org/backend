-- Add durable audit metadata and invoice identifiers for administrative order workflows.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS invoice_number VARCHAR(50);
CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_invoice_number ON orders(invoice_number) WHERE invoice_number IS NOT NULL;
