-- Migration: Add external_id field to pedidos table
-- Purpose: Support composite IDs (airport-orderNum) to prevent collisions when importing multiple order files
-- Date: 2025-13-11

-- Add external_id column if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'pedidos' 
        AND column_name = 'external_id'
    ) THEN
        ALTER TABLE pedidos ADD COLUMN external_id VARCHAR(50);
        RAISE NOTICE 'Column external_id added to pedidos table';
    ELSE
        RAISE NOTICE 'Column external_id already exists in pedidos table';
    END IF;
END $$;

-- Create unique index on external_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'pedidos' 
        AND indexname = 'idx_pedido_external_id'
    ) THEN
        CREATE UNIQUE INDEX idx_pedido_external_id ON pedidos(external_id);
        RAISE NOTICE 'Unique index idx_pedido_external_id created';
    ELSE
        RAISE NOTICE 'Unique index idx_pedido_external_id already exists';
    END IF;
END $$;

-- Verify the changes
SELECT 
    column_name, 
    data_type, 
    character_maximum_length,
    is_nullable
FROM information_schema.columns 
WHERE table_name = 'pedidos' 
AND column_name = 'external_id';

