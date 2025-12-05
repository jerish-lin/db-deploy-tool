-- Verification script for feature-12347-create-products-table.apply.sql
-- Check if products table exists with correct structure

-- Check table exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: products table exists'
    ELSE 'FAIL: products table does not exist'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='products';

-- Check columns exist
SELECT CASE 
    WHEN COUNT(*) = 10 THEN 'PASS: products table has all 10 columns'
    ELSE 'FAIL: products table has ' || COUNT(*) || ' columns (expected 10)'
END AS column_count_check
FROM pragma_table_info('products');

-- Check specific columns
SELECT 'PASS: column ' || name || ' exists' AS column_check
FROM pragma_table_info('products')
WHERE name IN ('id', 'name', 'description', 'price', 'category_id', 'sku', 'stock_quantity', 'created_at', 'updated_at', 'is_active');

-- Check indexes exist
SELECT CASE 
    WHEN COUNT(*) = 4 THEN 'PASS: products table has all 4 indexes'
    ELSE 'FAIL: products table has ' || COUNT(*) || ' indexes (expected 4)'
END AS index_count_check
FROM sqlite_master 
WHERE type='index' AND tbl_name='products' AND sql IS NOT NULL;

-- List indexes
SELECT 'Index: ' || name AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='products' AND sql IS NOT NULL;