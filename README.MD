# Pact V4 Contract Testing Guide

This guide provides a complete step-by-step setup for using **Pact V4** for contract testing with Java, Maven, and Spring Boot.

---

## 1. Prerequisites

Before starting, ensure you have the following installed:

- **Java 17+**
- **Maven 3.8+**
- **Docker** (for Pact Broker)
- **Git** (optional, for source control)
- **IDE** supporting Java 17 and JUnit 5 (IntelliJ IDEA recommended)

### Setup Pact Broker locally via Docker:

```bash
docker run -d -p 9292:9292 --name pact-broker pactfoundation/pact-broker
```

---

## 2. Maven Dependencies

Add the following dependencies to your `pom.xml`.

### Consumer Dependencies

```xml
<dependency>
    <groupId>au.com.dius.pact.consumer</groupId>
    <artifactId>junit5</artifactId>
    <version>4.6.15</version>
    <scope>test</scope>
</dependency>
```

### Provider Dependencies

```xml
<dependency>
    <groupId>au.com.dius.pact.provider</groupId>
    <artifactId>junit5</artifactId>
    <version>4.6.15</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### Maven Surefire Plugin (required for provider report generation)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.1.2</version>
    <configuration>
        <systemPropertyVariables>
            <pact.verifier.reportFormat>html</pact.verifier.reportFormat>
            <pact.verifier.reportDir>target/pact-reports</pact.verifier.reportDir>
            <pact.verifier.publishResults>true</pact.verifier.publishResults>
            <pact.provider.version>1.0.0</pact.provider.version>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

---

## 3. Writing a Consumer Pact Test (JUnit 5, V4)

```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "UserService", port = "8085", pactVersion = PactSpecVersion.V4)
class ConsumerPactTest {

    @Pact(provider = "UserService", consumer = "order_service_consumer")
    public V4Pact createUserPact(PactBuilder builder) {
        return builder
            .given("User with id 1 exists")
            .expectsToReceiveHttpInteraction("Request for user 1", http -> http
                .withRequest(req -> req.method("GET").path("/users/1"))
                .willRespondWith(res -> res.status(200)
                    .body(new PactDslJsonBody()
                        .numberType("id", 1)
                        .stringType("name", "John Doe")
                        .stringType("email", "john@example.com")
                    )
                )
            )
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createUserPact")
    void testGetUserFromProvider(MockServer mockServer) throws Exception {
        String response = Request.get(mockServer.getUrl() + "/users/1")
            .execute().returnContent().asString();

        assertTrue(response.contains("id"));
        assertTrue(response.contains("name"));
    }
}
```

### Key Notes:

- `@Pact` defines the contract
- `@PactTestFor` links the test to the contract
- `V4Pact` supports V4 interactions (sync & async)

---

## 4. Writing a Provider Pact Test (JUnit 5, Spring Boot)

```java
@Provider("UserService")
@PactBroker(url = "http://localhost:9292")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserServiceProviderTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void before(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @State("User with id 1 exists")
    public void userExists() {
        // Ensure user exists in DB
        userRepository.save(new User(1L, "John Doe", "john@example.com"));
    }

    @State("User with id 999 does not exist")
    public void userDoesNotExist() {
        // Ensure user 999 is absent
        userRepository.deleteById(999L);
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
```

### Key Annotations:

| Annotation | Purpose |
|---|---|
| `@Provider` | Name of the provider |
| `@PactBroker` | Fetch contracts from broker |
| `@SpringBootTest(webEnvironment=...)` | Start provider for HTTP testing |
| `@State` | Setup provider states (like DB entries) |
| `@TestTemplate` + `@ExtendWith(PactVerificationInvocationContextProvider.class)` | Required for V4 provider verification |

---

## 5. Running Tests and Generating Reports

Run provider verification with:

```bash
mvn clean test \
  -Dpact.verifier.reportFormat=html \
  -Dpact.verifier.reportDir=target/pact-reports \
  -Dpact.verifier.publishResults=true \
  -Dpact.provider.version=1.0.0
```

- HTML report will be in `target/pact-reports/index.html`
- Verification results are published to the Pact Broker

---

## 6. Publishing Consumer Pact

After running consumer tests:

```bash
pact-broker publish target/pacts \
  --consumer-app-version=2.0.0 \
  --broker-base-url=http://localhost:9292
```

**Important:** Use a unique consumer version for each run to avoid race conditions.

---

## 7. Best Practices

- Always use unique consumer versions when publishing contracts
- Implement all provider states for interactions to avoid skipped verifications
- Use V4 Pacts if you need asynchronous interactions or multiple request/response messages
- Always enable HTML reporting for CI/CD visibility
- Prefer Spring Boot Test with random ports to avoid port conflicts
- Separate consumer and provider tests in different Maven modules for clarity
- Use assertions with example values (avoid only type matchers) to prevent pact mutation

---

## 8. Useful Maven Properties

| Property | Default | Purpose |
|---|---|---|
| `pact.verifier.reportFormat` | none | html, markdown, console |
| `pact.verifier.reportDir` | target/pact/reports | Folder for report |
| `pact.verifier.publishResults` | false | Publish verification result to broker |
| `pact.provider.version` | none | Provider version in broker |
| `pact.broker.url` | none | URL of the Pact broker |

---

## 9. Recommended Folder Structure

```
project/
├─ consumer/
│  ├─ src/test/java/...ConsumerPactTest.java
│  └─ target/pacts
└─ provider/
   ├─ src/test/java/...ProviderPactTest.java
   └─ target/pact-reports
```

---

## 10. Run Order for CI/CD

1. Consumer tests → generate pact in `target/pacts`
2. Publish consumer pact to broker
3. Provider tests → verify pact, generate HTML report, publish verification
4. Can-I-Deploy → check provider/consumer compatibility before release

---

## Summary

This setup gives you a complete Pact V4 workflow, including consumer, broker, provider, verification, and reporting. It's production-ready for CI/CD pipelines.

If you want, I can also create a **ready-to-use Maven multi-module project skeleton** for Consumer + Provider + Broker using Pact V4 with all the annotations, reports, and example states already pre-configured. Let me know!
