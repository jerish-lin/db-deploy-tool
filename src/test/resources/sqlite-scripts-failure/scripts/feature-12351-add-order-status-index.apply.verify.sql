-- Verification script for feature-12351-add-order-status-index.apply.sql
-- Check if order status index exists

-- Check index exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: idx_orders_status index exists'
    ELSE 'FAIL: idx_orders_status index does not exist'
END AS index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_orders_status' AND tbl_name='orders';

-- List all indexes on orders table
SELECT 'Index: ' || name AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='orders' AND sql IS NOT NULL;