-- SQL script to update existing test accounts to proper SHA-256 password_hash values
-- Run this in SQL Server (SSMS) connected to the FPTEventManagement database.

UPDATE Users SET password_hash = '3107f4c1cfba53b084ab59058fb81a573d18e2691de833f0c928b033279043a0' WHERE email = 'an.nvse14001@fpt.edu.vn'; -- hash_pwd_1
UPDATE Users SET password_hash = '8cc4f0a7ec079a2989bd67eb7351f47f576fd5ee82c8259f9ba4ef8aabe22ff5' WHERE email = 'binh.ttse14002@fpt.edu.vn'; -- hash_pwd_2
UPDATE Users SET password_hash = '433e6e8cf5d146e3d40a710bc53af417569d83f64ab71b8402f6bc14b040ad9c' WHERE email = 'huy.lqclub@fpt.edu.vn'; -- hash_pwd_3
UPDATE Users SET password_hash = 'cbd2f0d75d0f33b2bdec633efa91dd380f613938fdafcde78d90829a607378ca' WHERE email = 'thu.pmso@fpt.edu.vn'; -- hash_pwd_4
UPDATE Users SET password_hash = 'b82749c79302960e184bc800084d987f9c6dd9237c7ab649224f416a179f0ea9' WHERE email = 'admin.event@fpt.edu.vn'; -- hash_admin

-- Verify
SELECT user_id, full_name, email, password_hash FROM Users WHERE email IN (
  'an.nvse14001@fpt.edu.vn',
  'binh.ttse14002@fpt.edu.vn',
  'huy.lqclub@fpt.edu.vn',
  'thu.pmso@fpt.edu.vn',
  'admin.event@fpt.edu.vn'
);
