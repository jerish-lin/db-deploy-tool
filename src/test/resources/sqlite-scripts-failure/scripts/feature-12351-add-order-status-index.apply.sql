-- Add index on order status
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);