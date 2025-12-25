-- Verification script for feature-12350-create-user-orders-view.apply.sql
-- This script should fail to execute, so verification should show the view does not exist

-- Another intentional error
SELECT * FROM nonexistent_table;