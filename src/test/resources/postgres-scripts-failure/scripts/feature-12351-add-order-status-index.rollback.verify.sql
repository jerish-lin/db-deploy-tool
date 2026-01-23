-- Verify index no longer exists
SELECT indexname FROM pg_indexes WHERE tablename = 'orders' AND indexname = 'idx_orders_status';