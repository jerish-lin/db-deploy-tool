-- Remove sample users added in this version
DELETE FROM users WHERE username IN ('jane_smith', 'bob_wilson', 'alice_johnson');