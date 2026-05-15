DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_status') THEN
        CREATE TYPE booking_status AS ENUM ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'COMPLETING', 'PAYMENT_PENDING', 'PAID', 'PAYMENT_FAILED', 'REFUNDED');
    END IF;
END
$$@@

DO $$
DECLARE
    status_value text;
BEGIN
    FOREACH status_value IN ARRAY ARRAY['COMPLETING', 'PAYMENT_PENDING', 'PAID', 'PAYMENT_FAILED', 'REFUNDED']
    LOOP
        IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_status')
           AND NOT EXISTS (
                SELECT 1
                FROM pg_enum e
                JOIN pg_type t ON t.oid = e.enumtypid
                WHERE t.typname = 'booking_status'
                  AND e.enumlabel = status_value
           ) THEN
            EXECUTE format('ALTER TYPE booking_status ADD VALUE %L', status_value);
        END IF;
    END LOOP;
END
$$@@

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_item_status') THEN
        CREATE TYPE booking_item_status AS ENUM ('PENDING', 'RESERVED', 'CONFIRMED', 'REFUNDED');
    END IF;
END
$$@@

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_item_status')
       AND NOT EXISTS (
            SELECT 1
            FROM pg_enum e
            JOIN pg_type t ON t.oid = e.enumtypid
            WHERE t.typname = 'booking_item_status'
              AND e.enumlabel = 'PENDING'
       ) THEN
        ALTER TYPE booking_item_status ADD VALUE 'PENDING' BEFORE 'RESERVED';
    END IF;
END
$$@@
