# Philosophy

- Open source
- Immutable ledger
- No dept ledger
- Java language
- Fast Integration

# Helper

Replace with yoru real database credentials

`mvn flyway:migrate "-Dflyway.url=jdbc:postgresql://localhost:5432/general-ledger" "-Dflyway.user=postgres" "-Dflyway.password=123456" "-Dflyway.schemas=general-ledger" "-Dflyway.locations=filesystem:db/migration"`