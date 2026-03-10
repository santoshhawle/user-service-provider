package org.example.userserviceprovider;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.VerificationReports;
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
@PactBroker(url="http://localhost:9292")  // Uncomment when Pact Broker is available
@ExtendWith(PactVerificationInvocationContextProvider.class)
@VerificationReports(value = {"markdown"}, reportDir = "target/myreports")
public class UserServiceProviderTest {

    // ============ Constants: Pact Configuration ============
    /** Port used by the provider service for contract verification */
    private static final String LOCALHOST = "localhost";

    /** System property key for pact verification report format */
    private static final String PACT_REPORT_FORMAT_KEY = "pact.verifier.reportFormat";

    /** Report format for pact verification results */
    private static final String REPORT_FORMAT_HTML = "html";

    /** System property key for pact verification report directory */
    private static final String PACT_REPORT_DIR_KEY = "pact.verifier.reportDir";

    /** Directory path where pact reports are generated */
    private static final String PACT_REPORT_DIR = "target/pact-reports";

    @LocalServerPort
    private int port;

    /**
     * Initializes system properties for pact verification reporting.
     * This static setup method runs once before all tests and configures:
     * 1. Report format (HTML) for enhanced readability
     * 2. Report directory location for easy access to verification results
     *
     * These properties enable detailed debugging and documentation of contract verification.
     */
    @BeforeAll
    static void setup() {
        // Configure HTML report format for better visualization of pact verification results
        System.setProperty(PACT_REPORT_FORMAT_KEY, REPORT_FORMAT_HTML);

        // Specify the output directory where verification reports will be stored
        System.setProperty(PACT_REPORT_DIR_KEY, PACT_REPORT_DIR);
    }

    /**
     * Sets up the Pact verification context before each test method.
     * Configures the HTTP target pointing to the actual provider service running on a random port.
     *
     * This method runs before each @TestTemplate method to ensure the context is properly
     * initialized with the correct service endpoint.
     *
     * @param context PactVerificationContext used to configure verification target
     */
    @BeforeEach
    void setup(PactVerificationContext context) {
        // Configure the HTTP target to point to the provider service running locally
        // on the dynamically allocated port (configured by @SpringBootTest)
        context.setTarget(new HttpTestTarget(LOCALHOST, port));
        System.setProperty("pact.verifier.publishResults","true");
        System.setProperty("pact.provider.version","1.0.0");
        System.setProperty("pact.provider.tag","feature_p_1");
    }

    /**
     * Template method for pact verification.
     * This method is invoked by JUnit5 once for each interaction defined in the pact.
     *
     * The Pact framework automatically generates multiple test invocations based on the
     * number of interactions in the loaded pact files, allowing verification of all
     * contract expectations.
     *
     * @param context PactVerificationContext containing the interaction to verify
     */
    @TestTemplate
    void pactVerificationTestTemplate(PactVerificationContext context) {
        // Verify the current interaction against the actual provider implementation.
        // The framework repeats this method call for each interaction in the pact.
        context.verifyInteraction();
    }

    /**
     * Provider state: "User with id 1 exists"
     *
     * This method prepares the provider service state before verifying interactions
     * that assume user with id 1 exists in the system.
     *
     * Currently, the provider is pre-configured with test data containing this user,
     * so no explicit action is required. This method serves as a placeholder for
     * potential setup logic (e.g., database seeding, mock data initialization).
     */
    @State("User with id 1 exists")
    void userWithIdOneExists() {
        // Provider state is ready: user with id 1 is pre-configured in test data
        // No additional setup required for this interaction
    }

    /**
     * Provider state: "User with id 999 does not exist"
     *
     * This method prepares the provider service state before verifying interactions
     * that expect a 404 response when requesting a non-existent user.
     *
     * Currently, the provider is pre-configured without this user ID in test data,
     * ensuring 404 responses. This method serves as a placeholder for potential
     * cleanup or state-specific setup logic.
     */
    @State("User with id 999 does not exist")
    void userWithIdNinetyNineNineDoesNotExist() {
        // Provider state is ready: user with id 999 is not in the system
        // This ensures the provider will return 404 for this interaction
    }

}

