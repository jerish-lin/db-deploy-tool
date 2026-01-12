-- Verify the data was deleted
SELECT 
    'Data deleted from ${table_name}' as verification_check,
    COUNT(*) as result
FROM ${table_name} 
WHERE name='${item_name}' AND status='${item_status}';