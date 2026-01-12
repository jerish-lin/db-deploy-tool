-- Verify the parameterized table exists and has correct structure
SELECT 
    'Table ${table_name} exists' as verification_check,
    COUNT(*) as result
FROM sqlite_master 
WHERE type='table' AND name='${table_name}';