-- THIS SCRIPT IS DESIGNED TO FAIL
-- Intentional SQL error: referencing non-existent column

CREATE VIEW user_orders_view AS
SELECT 
    u.id as user_id,
    u.username,
    u.email,
    o.non_existent_column as order_total,  -- This column doesn't exist and will cause failure
    o.order_date
FROM users u
LEFT JOIN orders o ON u.id = o.user_id;

-- Another intentional error
SELECT * FROM nonexistent_table;