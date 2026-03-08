package org.example.userserviceprovider;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Provider("UserService")  // Must match consumer test providerName
//@PactFolder("src/test/resources/pacts")     // Folder where the consumer pact json is stored
@PactBroker(url="http://localhost:9292")
@ExtendWith(PactVerificationInvocationContextProvider.class)
public class UserServiceProviderTest {

    @LocalServerPort
    private int port;

    @BeforeAll
    static void setup() {
        System.setProperty("pact.verifier.reportFormat", "html");
        System.setProperty("pact.verifier.reportDir", "target/pact-reports");
    }

    @BeforeEach
    void setup(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction(); // Verify each interaction from the pact
    }

    @State("User with id 1 exists")
    void userExists() {}


    // Provider state for 404 scenario
    @State("User with id 999 does not exist")
    public void userDoesNotExist() {
    }


}