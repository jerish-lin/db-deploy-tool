-- Verification script for feature-12351-add-order-status-index.rollback.sql
-- Check if order status index was dropped

-- Check index does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: idx_orders_status index was dropped'
    ELSE 'FAIL: idx_orders_status index still exists'
END AS index_check
FROM sqlite_master 
WHERE type='index' AND name='idx_orders_status' AND tbl_name='orders';

-- List remaining indexes on orders table
SELECT 'Index: ' || name AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='orders' AND sql IS NOT NULL;