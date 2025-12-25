CREATE VIEW user_orders_view AS
SELECT 
    u.id as user_id,
    u.username,
    u.email,
    o.order_date
FROM users u
LEFT JOIN orders o ON u.id = o.user_id;
