DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_status') THEN
        CREATE TYPE booking_status AS ENUM ('PENDING', 'CONFIRMED', 'IN_PROGRESS', 'CHECKED_IN', 'COMPLETED', 'CANCELLED');
    END IF;
END
$$@@

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_status')
       AND NOT EXISTS (
            SELECT 1
            FROM pg_enum e
            JOIN pg_type t ON t.oid = e.enumtypid
            WHERE t.typname = 'booking_status'
              AND e.enumlabel = 'IN_PROGRESS'
       ) THEN
        ALTER TYPE booking_status ADD VALUE 'IN_PROGRESS' AFTER 'CONFIRMED';
    END IF;
END
$$@@

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_item_status') THEN
        CREATE TYPE booking_item_status AS ENUM ('RESERVED', 'CONFIRMED', 'REFUNDED');
    END IF;
END
$$@@
