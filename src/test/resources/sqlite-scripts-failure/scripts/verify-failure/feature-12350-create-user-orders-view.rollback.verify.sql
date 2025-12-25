-- Verification script for feature-12350-create-user-orders-view.rollback.sql
-- Check if view was dropped (or never created due to failure)

-- Check view does not exist
SELECT CASE 
    WHEN COUNT(*) = 0 THEN 'PASS: user_orders_view does not exist (rollback successful or never created)'
    ELSE 'FAIL: user_orders_view still exists (rollback failed)'
END AS view_check
FROM sqlite_master 
WHERE type='view' AND name='user_orders_view';