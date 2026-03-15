4.1.4 A4. Verify and Complete the Root POM
After creating all 5 service modules:
a) Open the root eventticketing/pom.xml.
b) Confirm that <packaging>pom</packaging> exists.
c) Check whether all 5 services appear inside a <modules> block. If any are missing, add them
manually:
<modules>
<module>user-service</module>
<module>event-service</module>
<module>booking-service</module>
<module>ticket-service</module>
<module>sales-service</module>
</modules>
d) Save the file.
e) Reload Maven to pick up the changes.
f) Confirm there are no Maven errors in the tool window.
84.1.5 A5. Important: Do NOT Modify Child Service POMs
Each generated service already has its own <parent> block pointing to Spring Boot. Do not replace or
modify this parent. The root eventticketing POM acts only as an aggregator (it lists the modules
so you can build them all at once). It is not a Maven parent for the child modules.
In other words:
• Do not add a <parent> block pointing to eventticketing inside any child POM.
• Do not remove the existing Spring Boot <parent> from any child POM.
• The only connection between the root POM and the children is the <modules> block in the root.
4.1.6 Add jackson-databind Dependency
In each service’s pom.xml, add the following dependency inside the <dependencies> block (required for
Hibernate JSONB support):
<dependency>
<groupId>com.fasterxml.jackson.core</groupId>
<artifactId>jackson-databind</artifactId>
</dependency>
After adding, reload Maven.
4.1.7 Configure application.properties per Service
Inside each service’s src/main/resources/application.properties, add:
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/eventticketingdb
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=
org.hibernate.dialect.PostgreSQLDialect
All services use port 8080 internally. The port differentiation to 8081–8085 happens in docker-compose.yaml
(covered in the Dockerization section).
4.1.8 Create Sub-Packages
Inside each service’s main package (e.g., src/main/java/com/teamXX/eventticketing/user/), create
these sub-packages: model/, repository/, service/, controller/, dto/.
4.1.9 Add a Health Endpoint to Each Service
In each service’s controller/ package, create a simple health controller that exposes a GET endpoint
returning "OK" (200):
• User Service: GET /api/users/health
• Event Service: GET /api/events/health
• Booking Service: GET /api/bookings/health
9• Ticket Service: GET /api/tickets/health
• Sales Service: GET /api/sales/health
4.1.10 Create team.json
In the project root (next to the root pom.xml), create team.json as described in Section 2.
4.1.11 Verify the Build
Start PostgreSQL via Docker:
docker compose up -d
(This requires the docker-compose.yaml with just the PostgreSQL service — see Section 6.)
Then build all services from the project root:
mvn clean package -DskipTests
If the build succeeds, you should see BUILD SUCCESS and a JAR in each service’s target/ directory.
To test a single service locally:
cd user-service
mvn spring-boot:run
Then visit http://localhost:8080/api/users/health to confirm it responds with "OK".
4.2 Final Project Structure
After completing all steps, your project should look like this:
eventticketing/
+-- pom.xml (root aggregator POM, packaging=pom)
+-- team.json (team roster)
+-- docker-compose.yaml (PostgreSQL, later: all services)
+-- .gitignore
+-- user-service/
| +-- pom.xml (own Spring Boot parent, NOT eventticketing)
| +-- Dockerfile (added in Phase D)
| +-- src/main/java/com/teamXX/eventticketing/user/
| +-- UserApplication.java (@SpringBootApplication)
| +-- model/
| +-- repository/
| +-- service/
| +-- controller/
| +-- dto/
+-- event-service/
| +-- (same structure)
+-- booking-service/
| +-- (same structure)
+-- ticket-service/
| +-- (same structure)
+-- sales-service/
+-- (same structure)



section 6 :Database Setup (Docker Compose)
The docker-compose.yaml in the project root contains a PostgreSQL service with:
• Container name: eventticketing-db
• Port mapping: 5432:5432
• Environment variables: user postgres, password postgres, database eventticketingdb
• Named volume pgdata for data persistence
In Phase D (Dockerization), you will add all 5 application services to this same file.
Verify: Run docker compose up -d (PostgreSQL only at first), start any service locally, and confirm
it connects to the database
