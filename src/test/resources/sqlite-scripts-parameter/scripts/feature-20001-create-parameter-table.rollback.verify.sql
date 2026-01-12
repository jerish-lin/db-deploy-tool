-- Verify the parameterized table no longer exists
SELECT 
    'Table ${table_name} dropped' as verification_check,
    COUNT(*) as result
FROM sqlite_master 
WHERE type='table' AND name='${table_name}';