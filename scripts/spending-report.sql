SELECT
    TO_CHAR(transaction_date, 'YYYY-MM') AS month,
    subcategory,
    COUNT(*) AS transaction_count,
    SUM(amount) AS total_amount,
    ROUND(AVG(amount), 2) AS avg_amount
FROM transactions_flat
WHERE subcategory not in ('Payment', 'Vacation')
GROUP BY TO_CHAR(transaction_date, 'YYYY-MM'), subcategory
ORDER BY month DESC, total_amount DESC, subcategory;