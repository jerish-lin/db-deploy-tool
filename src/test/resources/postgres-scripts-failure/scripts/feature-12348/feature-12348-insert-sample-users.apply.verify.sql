-- Verify new users were inserted
SELECT COUNT(*) FROM users;
SELECT * FROM users WHERE username IN ('jane_smith', 'bob_wilson', 'alice_johnson');