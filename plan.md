# Context
We need to add a new platform-focused Spring Boot service (`hcau-platform-service`) plus a common library module (`hcau-platform-common`). These must follow the structure of existing modules (e.g., `hcau-banking-common`, `hcau-general-ledger`), reuse the same Maven parent/version/plugin conventions, and integrate with the README build flow so they can be built independently.

# Approach
1. **Create hcau-platform-common module**
   - Copy `pom.xml` structure from `hcau-banking-common/pom.xml`, update metadata (`artifactId`, `<name>`, `<description>`), and keep shared dependencies (Spring Data JPA, configuration processor, Lombok optional, ULID creator, Jackson, dependency on `hcau-banking-module-common`).
   - Reuse the same plugin stack (Spring Boot plugin skipped, resources copy, Spotless, Checkstyle, Jacoco).
   - Add `src/main/java/per/nonobeam/platform/common/PlatformCommonPackage.java` as a placeholder class plus `src/main/resources/checkstyle.xml` copied from another module.

2. **Create hcau-platform-service module**
   - Base the `pom.xml` on `hcau-general-ledger/pom.xml`: include Spring Boot starters for web, validation, data-jpa, actuator if needed, Kafka if desired, runtime PostgreSQL driver, Lombok optional, tests (spring-boot-starter-test, Testcontainers) and a dependency on the new `hcau-platform-common`.
   - Configure Flyway plugin pointing to `filesystem:db/migration`, include plugin dependencies for PostgreSQL as other services do, and replicate resources/Spotless/Checkstyle/Jacoco/Spring Boot plugins.
   - Add core structure:
     - `src/main/java/per/nonobeam/platform/service/PlatformServiceApplication.java` with `@SpringBootApplication`.
     - `src/main/resources/application.yml` mirroring other services’ placeholders (service name, datasource, Kafka configs commented or left blank).
     - `src/main/resources/checkstyle.xml`, `messages_en.properties`, `.env.example` if standard.
     - `src/main/resources/db/migration/V1__init.sql` placeholder or comment so Flyway runs.
     - Optionally add a simple controller or config class to ensure compilation.

3. **Repository integration**
   - Update repository-level README to list the two new modules and extend the build sequence to include `mvn -f hcau-platform-common/pom.xml clean install` before any service that depends on it, followed by `mvn -f hcau-platform-service/pom.xml clean install`.
   - Add module-specific README files if pattern exists.

# Verification
- `mvn -f hcau-platform-common/pom.xml clean install`
- `mvn -f hcau-platform-service/pom.xml clean install`
- Optional: `mvn -f hcau-platform-service/pom.xml spring-boot:run` to confirm the application starts with default config.
