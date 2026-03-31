-- Rename bucket type ACCOUNTED → AVAILABLE
UPDATE bucket_types SET name = 'AVAILABLE', description = 'Available balance' WHERE name = 'ACCOUNTED';

-- Rebuild internal_coa strings that contain ':ACCOUNTED' → ':AVAILABLE'
UPDATE accounts SET internal_coa = REPLACE(internal_coa, ':ACCOUNTED', ':AVAILABLE')
WHERE internal_coa LIKE '%:ACCOUNTED';
