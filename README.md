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

The project structure indicates a Java-based application. Complete this section with the technologies and versions configured in the repository.

- **Programming Language:** Java
- **Application Framework:** [Spring Boot]
- **Build Tool:** [Maven]
- **Database:** [PostgresSQL]
- **Testing Framework:** [JUnit]
- **Version Control:** Git

## 5. Project Structure

```text
ms03-alerts-and-emergencies/
├── database/
├── docs/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── edu/eci/dosw/campuseguro/alertas/
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
│           └── edu/eci/dosw/campuseguro/alertas/
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

Before running the application, ensure that the following tools are installed and configured:

- Java Development Kit (JDK), using the version required by the project (at least 21).
- The project's configured build tool, such as Maven.
- Git.
- Any database or external services required by the application.

### Clone the Repository

```bash
git clone <REPOSITORY_URL>
cd ms03-alerts-and-emergencies
```

Replace `<REPOSITORY_URL>` with the actual repository URL.

### Configure the Application

Review the configuration files in `src/main/resources/` and configure the required environment variables, database connection, credentials, and external service endpoints.

Do not commit passwords, access tokens, or other sensitive credentials to the repository.

### Update an Existing Database

Docker applies scripts in `database/` only when it initializes a new data volume. To update an existing database, apply the migration explicitly:

```powershell
docker compose exec -T postgres psql -U campusseguro -d campusseguro_alerts -f /docker-entrypoint-initdb.d/004_upgrade_alerts_schema.sql
```

### Build and Run

Use the commands corresponding to the build tool configured in the project.

For a Maven project with a Maven Wrapper:

**Linux/macOS:**

```bash
./mvnw clean install
./mvnw spring-boot:run
```

**Windows:**

```powershell
.\mvnw.cmd clean install
.\mvnw.cmd spring-boot:run
```

These commands apply if the repository contains a compatible Maven Wrapper and is configured as a Spring Boot application.

## 7. Testing

The project contains a test structure organized by component, including controllers, services, clients, mappers, exceptions, and security.

To execute the test suite in a Maven-based project, run:

**Linux/macOS:**

```bash
./mvnw test
```

**Windows:**

```powershell
.\mvnw.cmd test
```

Tests should verify the expected behavior of the application, including business rules, request validation, exception handling, service interactions, and security controls, as applicable.

Actually we have 78% of coverage with the implemented tests

![Jacoco](/docs/images/alerts_jacoco.png)

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