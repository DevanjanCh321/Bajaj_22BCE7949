package com.example.hiring.util;

public final class FinalSql {

    private FinalSql() {
    }

    /**
     * Final SQL string to be submitted in the body { "finalQuery": "..." }.
     * Uses only EXTRACT+CASE so it works across common RDBMS.
     */
    public static String build() {
        return "SELECT\n"
                + "  p.amount AS SALARY,\n"
                + "  CONCAT(e.first_name, ' ', e.last_name) AS NAME,\n"
                + "  (\n"
                + "    EXTRACT(YEAR FROM CURRENT_DATE) - EXTRACT(YEAR FROM e.dob)\n"
                + "    - CASE\n"
                + "        WHEN EXTRACT(MONTH FROM CURRENT_DATE) < EXTRACT(MONTH FROM e.dob)\n"
                + "          OR (EXTRACT(MONTH FROM CURRENT_DATE) = EXTRACT(MONTH FROM e.dob)\n"
                + "              AND EXTRACT(DAY FROM CURRENT_DATE) < EXTRACT(DAY FROM e.dob))\n"
                + "        THEN 1 ELSE 0\n"
                + "      END\n"
                + "  ) AS AGE,\n"
                + "  d.department_name AS DEPARTMENT_NAME\n"
                + "FROM payments p\n"
                + "JOIN employee e ON e.emp_id = p.emp_id\n"
                + "JOIN department d ON d.department_id = e.department\n"
                + "WHERE EXTRACT(DAY FROM p.payment_time) <> 1\n"
                + "  AND p.amount = (\n"
                + "      SELECT MAX(p2.amount)\n"
                + "      FROM payments p2\n"
                + "      WHERE EXTRACT(DAY FROM p2.payment_time) <> 1\n"
                + "  );\n";
    }
}
