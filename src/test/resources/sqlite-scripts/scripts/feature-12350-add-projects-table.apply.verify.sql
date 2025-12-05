-- Verification script for feature-12350-add-projects-table.apply.sql
-- Check if projects table exists with correct structure and data

-- Check table exists
SELECT CASE 
    WHEN COUNT(*) > 0 THEN 'PASS: projects table exists'
    ELSE 'FAIL: projects table does not exist'
END AS table_check
FROM sqlite_master 
WHERE type='table' AND name='projects';

-- Check columns exist
SELECT CASE 
    WHEN COUNT(*) = 7 THEN 'PASS: projects table has all 7 columns'
    ELSE 'FAIL: projects table has ' || COUNT(*) || ' columns (expected 7)'
END AS column_count_check
FROM pragma_table_info('projects');

-- Check specific columns
SELECT 'PASS: column ' || name || ' exists' AS column_check
FROM pragma_table_info('projects')
WHERE name IN ('project_id', 'project_name', 'start_date', 'end_date', 'budget', 'status', 'created_at');

-- Check indexes exist
SELECT CASE 
    WHEN COUNT(*) = 2 THEN 'PASS: projects table has all 2 indexes'
    ELSE 'FAIL: projects table has ' || COUNT(*) || ' indexes (expected 2)'
END AS index_count_check
FROM sqlite_master 
WHERE type='index' AND tbl_name='projects' AND sql IS NOT NULL;

-- Check sample data was inserted
SELECT CASE 
    WHEN COUNT(*) = 3 THEN 'PASS: All 3 sample projects were inserted'
    ELSE 'FAIL: Only ' || COUNT(*) || ' sample projects found (expected 3)'
END AS data_check
FROM projects;

-- List indexes
SELECT 'Index: ' || name AS index_list
FROM sqlite_master 
WHERE type='index' AND tbl_name='projects' AND sql IS NOT NULL;