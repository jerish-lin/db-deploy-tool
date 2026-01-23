-- Verify the sample users were deleted
SELECT COUNT(*) FROM users WHERE username IN ('jane_smith', 'bob_wilson', 'alice_johnson');