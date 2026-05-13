DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ticket_sale_method') THEN
        CREATE TYPE ticket_sale_method AS ENUM ('CREDIT_CARD', 'DEBIT_CARD', 'WALLET');
    END IF;
END
$$@@

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ticket_sale_status') THEN
        CREATE TYPE ticket_sale_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED');
    END IF;
END
$$@@

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'promotion_discount_type') THEN
        CREATE TYPE promotion_discount_type AS ENUM ('PERCENTAGE', 'FIXED');
    END IF;
END
$$@@

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ticket_sales')
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_ticket_sales_booking_id') THEN
        ALTER TABLE ticket_sales ADD CONSTRAINT uk_ticket_sales_booking_id UNIQUE (booking_id);
    END IF;
END
$$@@
