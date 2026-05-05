DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_status') THEN
        CREATE TYPE booking_status AS ENUM ('REQUESTED', 'PENDING', 'CONFIRMED', 'IN_PROGRESS', 'CHECKED_IN', 'COMPLETED', 'CANCELLED');
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
              AND e.enumlabel = 'REQUESTED'
       ) THEN
        ALTER TYPE booking_status ADD VALUE 'REQUESTED';
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
