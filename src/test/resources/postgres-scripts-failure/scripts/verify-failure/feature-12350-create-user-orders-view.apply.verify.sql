-- THIS VERIFICATION SCRIPT IS DESIGNED TO FAIL
-- Intentional error: referencing non-existent column

SELECT 
    u.id as user_id,
    u.username,
    u.email,
    o.non_existent_column as order_total,  -- This column doesn't exist
    o.order_date
FROM users u
LEFT JOIN orders o ON u.id = o.user_id;