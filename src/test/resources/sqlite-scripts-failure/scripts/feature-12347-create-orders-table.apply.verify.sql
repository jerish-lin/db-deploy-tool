-- Verification script for feature-12347-create-orders-table.apply.sql
-- Check if orders table exists with correct structure and data

-- Check table exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: orders table exists'
    ELSE 'FAIL: orders table does not exist'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='orders';

-- Check columns exist
SELECT CASE 
    WHEN COUNT(*) = 5 THEN 'PASS: orders table has all 5 columns'
    ELSE 'FAIL: orders table has ' || COUNT(*) || ' columns (expected 5)'
END AS column_count_check
FROM pragma_table_info('orders');

-- Check specific columns
SELECT 'PASS: column ' || name || ' exists' AS column_check
FROM pragma_table_info('orders')
WHERE name IN ('id', 'user_id', 'order_date', 'total_amount', 'status');

-- Check initial data was inserted
SELECT CASE 
    WHEN COUNT(*) = 2 THEN 'PASS: All 2 initial orders were inserted'
    ELSE 'FAIL: Only ' || COUNT(*) || ' initial orders found (expected 2)'
END AS data_check
FROM orders;

-- Check foreign key relationship exists
SELECT CASE 
    WHEN COUNT(*) = 2 THEN 'PASS: Orders reference both users'
    ELSE 'FAIL: Only ' || COUNT(*) || ' orders have valid user references'
END AS fk_check
FROM orders o
JOIN users u ON o.user_id = u.id;