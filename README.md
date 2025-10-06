# webhook-sql (Spring Boot)

Automates the qualifier flow:

1. POST `/hiring/generateWebhook/JAVA` with your name, regNo, email.
2. Compute the SQL query for the "younger employees per department" problem.
3. POST the query to the returned `webhook` URL (or to `/hiring/testWebhook/JAVA` if none) with `Authorization: Bearer <accessToken>`.

## Build **without** installing JDK (Dockerized Maven)

```bash
docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-21 mvn -q -DskipTests package
```

The runnable JAR will be at `target/webhook-sql-0.0.1-SNAPSHOT.jar` and the query is also saved to `target/final-query.sql`.

## Run (still without local JDK)

```bash
docker run --rm -v "$PWD":/app -w /app eclipse-temurin:21-jre java -jar target/webhook-sql-0.0.1-SNAPSHOT.jar
```

You can pass your info via env vars:

```bash
docker run --rm -e NAME="Ayush Kushwaha" -e REGNO="REG12347" -e EMAIL="ayush@example.com" \
  -v "$PWD":/app -w /app eclipse-temurin:21-jre \
  java -jar target/webhook-sql-0.0.1-SNAPSHOT.jar
```

## Endpoints used

- Generate: `https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA`
- Submit (fallback): `https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA`

## Final SQL

```sql
SELECT e.emp_id, e.first_name, e.last_name, d.department_name,
       (SELECT COUNT(*)
          FROM employee e2
         WHERE e2.department = e.department
           AND e2.dob > e.dob) AS younger_employees_count
FROM employee e
JOIN department d ON d.department_id = e.department
ORDER BY e.emp_id DESC;
```
