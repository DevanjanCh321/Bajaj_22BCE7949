SELECT
  p.amount AS SALARY,
  CONCAT(e.first_name, ' ', e.last_name) AS NAME,
  (
    EXTRACT(YEAR FROM CURRENT_DATE) - EXTRACT(YEAR FROM e.dob)
    - CASE
        WHEN EXTRACT(MONTH FROM CURRENT_DATE) < EXTRACT(MONTH FROM e.dob)
          OR (EXTRACT(MONTH FROM CURRENT_DATE) = EXTRACT(MONTH FROM e.dob)
              AND EXTRACT(DAY FROM CURRENT_DATE) < EXTRACT(DAY FROM e.dob))
        THEN 1 ELSE 0
      END
  ) AS AGE,
  d.department_name AS DEPARTMENT_NAME
FROM payments p
JOIN employee e ON e.emp_id = p.emp_id
JOIN department d ON d.department_id = e.department
WHERE EXTRACT(DAY FROM p.payment_time) <> 1
  AND p.amount = (
      SELECT MAX(p2.amount)
      FROM payments p2
      WHERE EXTRACT(DAY FROM p2.payment_time) <> 1
  );
