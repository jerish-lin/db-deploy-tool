-- Verify the data was inserted
SELECT 
    'Data inserted into ${table_name}' as verification_check,
    COUNT(*) as result
FROM ${table_name} 
WHERE name='${item_name}' AND status='${item_status}';