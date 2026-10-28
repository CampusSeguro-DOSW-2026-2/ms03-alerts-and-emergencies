# ms03-alerts-and-emergencies
MS03 - Alerts creation, activation, distribution, and monitoring service.

## 1. Project Overview

MS03 is a microservice responsible for managing alerts and emergencies within the CampusSeguro platform. Its main purpose is to support the creation, activation, distribution, and monitoring of alerts to help communicate relevant information about emergency situations and drills.

The service is organized into modular components to support maintainability, testing, and integration with other services in the platform in future sprints.

## 2. Team Information

### Team Members

| No. | Full Name | 
|---|---|
| 1 | José Daniel García Pineda |
| 2 | Paula Alejandra Novoa Castellanos |
| 3 | Jeronimo Moreno Herrera |
| 4 | Derly Valeria Pachón Pinzón |

### Responsible Squad

- **Squad Name:** Zephyros
- **Project:** CampusSeguro
- **Module:** MS03 - Alerts and Emergencies
- **Repository:** `ms03-alerts-and-emergencies`

## 3. Main Responsibilities

The microservice is intended to support the following responsibilities:

- **Alert Creation:** Manage the creation of alerts associated with emergency situations.
- **Alert Activation:** Support the activation of alerts when an emergency/drill needs to be communicated.
- **Push Distribution:** Support the distribution of alerts through mail notifications.
- **Alert Monitoring:** Provide mechanisms for monitoring alerts and tracking their relevant information.
- **Alert Management:** Organize alert-related information and operations through dedicated application components.

## 4. Technology Stack

- **Programming Language:** Java 21
- **Application Framework:** Spring Boot 4.1 (Web MVC, Data JPA, Security with JWT resource server, Validation)
- **Build Tool:** Maven
- **Database:** PostgreSQL 17 on Docker
- **Email:** Brevo (simulated in the local profile)
- **API documentation:** springdoc-openapi (Swagger UI) and `docs/api/openapi.yaml`
- **Testing:** JUnit 5, Mockito, MockMvc, WireMock, Testcontainers, JaCoCo
- **Static analysis:** SonarQube
- **Version Control:** Git

## 5. Project Structure

```text
ms03-alerts-and-emergencies/
├── database/
├── docs/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── edu/eci/dosw/campusseguro/alertas/
│   │   │       ├── client/
│   │   │       ├── config/
│   │   │       ├── controller/
│   │   │       ├── dto/
│   │   │       ├── entity/
│   │   │       ├── exception/
│   │   │       ├── mapper/
│   │   │       ├── repository/
│   │   │       ├── scheduler/
│   │   │       ├── security/
│   │   │       ├── service/
│   │   │       ├── validation/
│   │   │       └── AlertsApplication.java
│   │   └── resources/
│   └── test/
│       └── java/
│           └── edu/eci/dosw/campusseguro/alertas/
│               ├── client/
│               ├── controller/
│               ├── exception/
│               ├── mapper/
│               ├── security/
│               ├── service/
│               └── AlertsApplicationTest.java
└── README.md
```


## 6. Installation and Execution

### Prerequisites

- JDK 21 or newer.
- Maven 3.9+ (the repository has no Maven Wrapper).
- Docker Desktop (PostgreSQL and the integration tests run on Docker).

### Run locally

1. Start PostgreSQL 17. The scripts in `database/` run in order the first time the volume is created, including `005_sample_data.sql`, which loads one alert in each lifecycle state:

   ```bash
   docker compose up -d
   ```

   To reload the scripts from scratch: `docker compose down -v && docker compose up -d`.

2. Start the service with the default `local` profile (http://localhost:8080, Swagger at http://localhost:8080/swagger-ui.html):

   ```bash
   mvn spring-boot:run
   ```

The `local` profile needs no external services:

- **MS01/MS02** are replaced by `src/main/resources/catalog-stub.json` (buildings, zones, roles and users).
- **Email** is simulated: each notification is written to the log and recorded as sent. To send real email through Brevo, set `MAIL_PROVIDER=brevo` and `BREVO_API_KEY` in a `.env` file (ignored by Git).
- **Tokens:** `GET http://localhost:8080/local/tokens` returns a JWT for each role:

  | Key | Use |
  |---|---|
  | `COORDINATOR` | Creates, edits and manages the lifecycle of alerts |
  | `AUDITOR` | Reads alerts and their audit history |
  | `SERVICE_MS06` | Creates, activates and ends simulation alerts |
  | `RECIPIENT` | User `usr-002` with role `STUDENT`; can view and confirm the sample ACTIVE alert |
  | `USER` | Authenticated user who is not a recipient (gets 403) |

The Postman collection in `docs/postman/` covers the full contract: `npx newman run docs/postman/ms03-alertas.postman_collection.json` with the service running.

Never commit passwords, access tokens or other credentials to the repository.

## 7. Testing

```bash
mvn test     # unit tests (JUnit 5, Mockito, MockMvc, WireMock)
mvn verify   # also integration tests *IT with Testcontainers (requires Docker) and the JaCoCo report
```

`mvn verify` fails if line coverage drops below 80%. The current line coverage is about 88%; the HTML report is generated in `target/site/jacoco/index.html`.

### Static Analysis (SonarQube)

The project is analyzed with SonarQube, using the JaCoCo XML report (`target/site/jacoco/jacoco.xml`) as the coverage source.

**Run it locally:**

1. Start SonarQube (http://localhost:9000, first login `admin`/`admin`):

   ```bash
   docker compose --profile sonar up -d sonarqube
   ```

2. In SonarQube, create a local project with key `ms03-alerts-and-emergencies` and generate a token.
3. Build, test and analyze (Docker must be running for the `*IT` tests):

   ```bash
   mvn clean verify sonar:sonar -Dsonar.token=<YOUR_TOKEN>
   ```

   Never commit the token; pass it on the command line or through the `SONAR_TOKEN` environment variable.

**Continuous integration:** `.github/workflows/sonarqube.yml` runs the analysis on every push to `main`/`develop` and on pull requests, and fails if the Quality Gate is not met. It requires the repository secrets `SONAR_TOKEN` and `SONAR_HOST_URL`.

## 8. Diagrams

### DataBase Diagram

![DataBase Diagram](/docs/images/diagramdb.jpeg)

### DataBase Diagram

![Class Diagram](/docs/images/diagramaClases.jpeg)

### Context Diagram

![context Diagram](/docs/images/diagramaContexto.png)

### Components Diagram

![Components Diagram](/docs/images/diagramaComponentes.png)


---

**MS03 - Alerts and Emergencies**

*Campus Seguro — Alert Management and Emergency Monitoring Service*