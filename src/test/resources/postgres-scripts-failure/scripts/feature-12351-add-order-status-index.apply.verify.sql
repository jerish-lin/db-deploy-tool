-- Verify index exists
SELECT indexname FROM pg_indexes WHERE tablename = 'orders' AND indexname = 'idx_orders_status';