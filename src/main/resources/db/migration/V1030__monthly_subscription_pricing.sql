UPDATE prices
SET interval_type = 'month',
    interval_count = 1,
    amount = 50000,
    currency = 'vnd'
WHERE stripe_price_id = 'BASIC_TRIP_50000_VND';

UPDATE prices
SET interval_type = 'month',
    interval_count = 1,
    amount = 100000,
    currency = 'vnd'
WHERE stripe_price_id = 'GROUP_PRO_TRIP_100000_VND';

UPDATE products
SET description = 'Cho phượt thủ đơn độc - thanh toán theo tháng'
WHERE stripe_product_id = 'BASIC_TRIP';

UPDATE products
SET description = 'Cho nhóm 2-4 người - thanh toán theo tháng'
WHERE stripe_product_id = 'GROUP_PRO_TRIP';
