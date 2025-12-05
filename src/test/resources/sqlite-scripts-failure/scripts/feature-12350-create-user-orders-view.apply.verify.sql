-- Verification script for feature-12350-create-user-orders-view.apply.sql
-- This script should fail to execute, so verification should show the view does not exist

-- Check view does not exist (since script should have failed)
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: user_orders_view does not exist (as expected for failed script)'
    ELSE 'FAIL: user_orders_view exists (unexpected for failed script)'
END AS view_check
FROM sqlite_master 
WHERE type='view' AND name='user_orders_view';