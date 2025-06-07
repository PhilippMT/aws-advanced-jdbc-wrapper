package software.amazon.jdbc.example.springbootaurora;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.jdbc.Connection; // AWS JDBC Connection
import software.amazon.jdbc.HostSpec;
import software.amazon.jdbc.plugin.aurora.AuroraAdvancedFeaturesPlugin; // To get attribute name
import software.amazon.jdbc.example.springbootaurora.model.Customer;
import software.amazon.jdbc.example.springbootaurora.repository.CustomerRepository;
import software.amazon.jdbc.example.springbootaurora.service.CustomerService;

@SpringBootTest
// @ActiveProfiles("test") // Uncomment if you have an application-test.properties
public class SpringBootAuroraAdvancedPluginIntegrationTest {

    @Autowired
    private DataSource dataSource; // The wrapped DataSource

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testApplicationContextLoads() {
        assertNotNull(dataSource);
        assertNotNull(customerRepository);
        assertNotNull(customerService);
        assertNotNull(jdbcTemplate);
        System.out.println("Application context loaded successfully.");
    }

    @Test
    void testFlywayMigrationsWithStrictWriter() {
        // V1 creates the table.
        // V2 attempts to set strict_writer=true and inserts "Flyway Initial Customer - From V2"
        // and adds a column.

        // Verify the customer inserted by Flyway V2 script exists
        List<Customer> customers = customerRepository.findAll();
        Optional<Customer> flywayCustomer = customers.stream()
            .filter(c -> "Flyway Initial Customer - From V2".equals(c.getName()))
            .findFirst();
        assertTrue(flywayCustomer.isPresent(), "Customer inserted by Flyway V2 should exist.");

        // Verify the schema change from V2 (new column)
        // This indirectly confirms V2 ran.
        // A simple way to check is to see if a query referencing the column works.
        // Note: This doesn't confirm strict-writer *blocked* anything, only that V2 ran.
        // If strict-writer was ON and Flyway connected to a non-forwarding reader,
        // the plugin should have thrown an exception during migration, failing the app startup.
        // So, successful startup and this check imply it worked as expected with the writer.
        try {
            jdbcTemplate.queryForList("SELECT id, name, created_at FROM customers LIMIT 1");
            System.out.println("Flyway V2 schema change (created_at column) verified.");
        } catch (Exception e) {
            fail("Schema change from Flyway V2 (created_at column) not found or query failed: " + e.getMessage());
        }
        System.out.println("Flyway migrations completed successfully, including V2 with strict-writer attempt.");
    }

    @Test
    @Transactional // Use transactional to roll back DB changes from this test
    void testCrudOperations() {
        // Create
        Customer newCustomer = customerService.createCustomer("John Doe");
        assertNotNull(newCustomer);
        assertNotNull(newCustomer.getId());
        assertEquals("John Doe", newCustomer.getName());
        System.out.println("Created customer: " + newCustomer);

        // Read
        Optional<Customer> foundCustomer = customerService.findCustomer(newCustomer.getId());
        assertTrue(foundCustomer.isPresent());
        assertEquals("John Doe", foundCustomer.get().getName());
        System.out.println("Found customer: " + foundCustomer.get());

        // List
        customerService.createCustomer("Jane Smith");
        List<Customer> allCustomers = customerService.listCustomers();
        assertTrue(allCustomers.stream().anyMatch(c -> "John Doe".equals(c.getName())));
        assertTrue(allCustomers.stream().anyMatch(c -> "Jane Smith".equals(c.getName())));
        System.out.println("Listed customers, found at least John Doe and Jane Smith.");

        // Update and Delete can be added if desired, but basic CRUD is covered.
    }

    @Test
    @Transactional // Rollback any DB changes
    void testStrictWriterModeAtRuntime() {
        // Scenario 1 & 2: Strict writer ON, on writer - should succeed.
        // We assume the DB connection here is to the writer.
        // Verification of blocking on a reader is complex in this setup.

        System.out.println("Testing strict writer mode at runtime...");
        try {
            // Enable strict-writer mode using the plugin's SQL command
            jdbcTemplate.execute("SET SESSION aws_advanced_jdbc.strict_writer = 'true'");
            System.out.println("Executed: SET SESSION aws_advanced_jdbc.strict_writer = 'true'");

            // Verify attribute is set (indirectly, by checking plugin's behavior if possible, or assume it worked)
            // Direct check of plugin's session attribute from here is not straightforward.
            // We rely on the plugin correctly parsing this and enforcing it.

            // Perform a write operation
            String customerName = "Runtime Strict Writer Test";
            jdbcTemplate.update("INSERT INTO customers (name) VALUES (?)", customerName);
            System.out.println("Executed INSERT with strict_writer=true");

            Customer found = customerRepository.findAll().stream()
                .filter(c -> customerName.equals(c.getName())).findFirst().orElse(null);
            assertNotNull(found, customerName + " should have been inserted with strict-writer ON (assuming connected to writer).");
            System.out.println(customerName + " successfully inserted.");

            // Scenario 3: Strict writer OFF
            jdbcTemplate.execute("SET SESSION aws_advanced_jdbc.strict_writer = 'false'");
            System.out.println("Executed: SET SESSION aws_advanced_jdbc.strict_writer = 'false'");

            String customerNameNonStrict = "Runtime Non-Strict Test";
            jdbcTemplate.update("INSERT INTO customers (name) VALUES (?)", customerNameNonStrict);
            System.out.println("Executed INSERT with strict_writer=false");

            Customer foundNonStrict = customerRepository.findAll().stream()
                .filter(c -> customerNameNonStrict.equals(c.getName())).findFirst().orElse(null);
            assertNotNull(foundNonStrict, customerNameNonStrict + " should have been inserted with strict-writer OFF.");
            System.out.println(customerNameNonStrict + " successfully inserted.");

        } catch (Exception e) {
            fail("Test failed due to an exception during strict writer runtime tests: " + e.getMessage(), e);
        }
    }

    @Test
    void testDataSourceIsAwsWrapped() throws Exception {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            assertTrue(conn.isWrapperFor(software.amazon.jdbc.Connection.class), "DataSource should provide AWS JDBC Connection");
            Connection awsConn = conn.unwrap(software.amazon.jdbc.Connection.class);
            assertNotNull(awsConn, "AWS Connection unwrap should not be null");
            HostSpec hostSpec = awsConn.getHostSpec();
            assertNotNull(hostSpec, "HostSpec should not be null");
            System.out.println("Connected to (AWS Wrapper): " + hostSpec.getHost() + ":" + hostSpec.getPort() + " with role " + hostSpec.getRole());

            // Check if our plugin is active by trying to set its known session var and see if it's handled (no error)
            // This doesn't confirm the plugin is "auroraAdvancedFeatures" but that some plugin handled it.
            // For more specific check, would need access to ConnectionPluginManager or similar.
            assertDoesNotThrow(() -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET SESSION aws_advanced_jdbc.strict_writer = 'true'"); // Should be consumed by plugin
                    stmt.execute("SET SESSION aws_advanced_jdbc.strict_writer = 'false'");
                }
            }, "Plugin should handle custom SET SESSION command without erroring at DB level.");
            System.out.println("Custom SET SESSION command handled as expected (no DB error).");
        }
    }
}
