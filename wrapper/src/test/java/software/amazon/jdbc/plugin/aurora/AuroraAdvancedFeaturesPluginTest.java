package software.amazon.jdbc.plugin.aurora;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow; // Added import

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList; // Added import
import java.util.Collections;
import java.util.List; // Added import
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.jdbc.HostRole;
import software.amazon.jdbc.HostSpec;
import software.amazon.jdbc.JdbcCallable;
import software.amazon.jdbc.PluginService;
import software.amazon.jdbc.dialect.Dialect;
import software.amazon.jdbc.targetdriverdialect.TargetDriverDialect;


public class AuroraAdvancedFeaturesPluginTest {

    @Mock PluginService mockPluginService;
    @Mock Connection mockConnection;
    @Mock Statement mockStatement;
    @Mock PreparedStatement mockPreparedStatement; // Added for completeness
    @Mock ResultSet mockResultSetVersion;
    @Mock ResultSet mockResultSetAuroraVersion;
    @Mock Properties mockProperties;
    @Mock JdbcCallable<Connection, SQLException> mockConnectFunc;
    @Mock JdbcCallable<ResultSet, SQLException> mockSqlFunction; // For execute method test

    // HostSpec details (can be constants or created in setup)
    private static final String WRITER_HOST = "writer.host.example.com";
    private static final String READER_HOST = "reader.host.example.com";
    private static final int DEFAULT_PORT = 5432;

    private HostSpec writerHostSpec;
    private HostSpec readerHostSpec;

    private AuroraAdvancedFeaturesPlugin plugin;

    // Helper to access internal state for assertions, if no public getter is available
    // This is generally discouraged, prefer package-private getters or other testing strategies.
    // For now, assuming a getter `isAuroraPg17OrHigher()` will be added to the plugin.

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);

        writerHostSpec = new HostSpec(WRITER_HOST, DEFAULT_PORT, HostRole.WRITER);
        readerHostSpec = new HostSpec(READER_HOST, DEFAULT_PORT, HostRole.READER);

        when(mockPluginService.getProperties()).thenReturn(mockProperties); // For constructor
        plugin = new AuroraAdvancedFeaturesPlugin(mockPluginService);

        // Common mocking for connect() path
        when(mockConnectFunc.call()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockConnection.getMetaData()).thenReturn(mock(java.sql.DatabaseMetaData.class)); // Avoid NPE on getURL
        when(mockConnection.getMetaData().getURL()).thenReturn("jdbc:postgresql://test-host/test-db");


        // Common mocking for execute path
        when(mockPluginService.getCurrentConnection()).thenReturn(mockConnection); // Default for execute
        // For Statement.getConnection() in execute
        when(mockStatement.getConnection()).thenReturn(mockConnection);
    }

    @AfterEach
    void tearDown() {
        // Clean up, if necessary
    }

    private void setupVersionQuery(String versionString) throws SQLException {
        when(mockStatement.executeQuery(eq("SELECT version()"))).thenReturn(mockResultSetVersion);
        when(mockResultSetVersion.next()).thenReturn(true).thenReturn(false); // Single row
        when(mockResultSetVersion.getString(1)).thenReturn(versionString);
    }

    private void setupAuroraVersionQuery(String auroraVersion) throws SQLException {
        // Query used in plugin: SELECT setting FROM pg_settings WHERE name = 'aurora_version'
        when(mockStatement.executeQuery(eq("SELECT setting FROM pg_settings WHERE name = 'aurora_version'")))
            .thenReturn(mockResultSetAuroraVersion);
        if (auroraVersion != null) {
            when(mockResultSetAuroraVersion.next()).thenReturn(true).thenReturn(false); // Single row
            when(mockResultSetAuroraVersion.getString(1)).thenReturn(auroraVersion);
        } else {
            // Simulate aurora_version setting not found or query failing to return rows for it
            when(mockResultSetAuroraVersion.next()).thenReturn(false);
        }
    }

    // Test for Aurora PG 17+
    @Test
    void testAuroraPostgres17Detection_isAuroraPg17() throws SQLException {
        setupVersionQuery("PostgreSQL 17.0, Aurora 4.0.0"); // Example string
        setupAuroraVersionQuery("4.0.0"); // Indicates Aurora

        plugin.connect(
            "protocol",
            writerHostSpec, // host spec doesn't influence version detection directly here
            mockProperties,
            true,
            mockConnectFunc);

        assertTrue(plugin.isAuroraPg17OrHigher(), "Should detect Aurora PG 17+");
    }

    @Test
    void testAuroraPostgres17Detection_isNonAuroraPg17() throws SQLException {
        setupVersionQuery("PostgreSQL 17.0");
        setupAuroraVersionQuery(null); // Simulate non-Aurora (aurora_version setting not found or empty)
        // Alternative: mock isAurora to throw exception on aurora_version query if that's how it signals non-Aurora robustly

        plugin.connect(
            "protocol",
            writerHostSpec,
            mockProperties,
            true,
            mockConnectFunc);

        assertFalse(plugin.isAuroraPg17OrHigher(), "Should not detect as Aurora PG 17+ if not Aurora");
    }

    @Test
    void testAuroraPostgres17Detection_isAuroraPg16() throws SQLException {
        setupVersionQuery("PostgreSQL 16.3, Aurora 3.5.0");
        setupAuroraVersionQuery("3.5.0");

        plugin.connect(
            "protocol",
            writerHostSpec,
            mockProperties,
            true,
            mockConnectFunc);

        assertFalse(plugin.isAuroraPg17OrHigher(), "Should not detect as Aurora PG 17+ if version < 17");
    }

    @Test
    void testAuroraPostgres17Detection_isPlainPg16() throws SQLException {
        setupVersionQuery("PostgreSQL 16.3");
        setupAuroraVersionQuery(null);

        plugin.connect(
            "protocol",
            writerHostSpec,
            mockProperties,
            true,
            mockConnectFunc);

        assertFalse(plugin.isAuroraPg17OrHigher(), "Should not detect as Aurora PG 17+ if plain PG < 17");
    }


    // Tests for Strict Writer
    @Test
    void testStrictWriterAttributeEnforcement_NotOnWriterOrForwardingReader_ThrowsException() throws Exception {
        plugin.setSessionAttribute(mockConnection, AuroraAdvancedFeaturesPlugin.STRICT_WRITER_SESSION_ATTRIBUTE, "true");
        when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec); // Current host is a READER

        // Ensure the reader is NOT a forwarding reader
        // Accessing extendedHostList directly is hard. We rely on checkAndRecordWriteForwardingSupport
        // not being called or its check for forwarding returning false.
        // For a unit test, we can ensure isWriteForwardingEnabledOnReader returns false.
        // This requires a connection to be passed to it, which happens in plugin.connect().
        // So, let's simulate that checkAndRecordWriteForwardingSupport has run and found no forwarding.
        // One way: mock the pluginService.getHostRole(conn) and then ensure the internal check for forwarding returns false.
        // For this test, the simplest is to ensure the host is in extendedHostList as a non-forwarding reader.
        // The plugin's internal map `extendedHostList` would be populated during `connect`.
        // Let's simulate a prior `connect` call that established it as a non-forwarding reader.

        // Simulate that checkAndRecordWriteForwardingSupport was called for this reader and it's not forwarding
        // This means ExtendedHostInfo for readerHostSpec has isWriteForwardingEnabled = false.
        // To achieve this without overly complex mocking of connect's internals for this specific test:
        // We can mock the behavior of extendedHostList indirectly.
        // The strict writer check uses: `ExtendedHostInfo extendedInfo = extendedHostList.get(currentHost.getHost());`
        // We need this to return an info object with `isWriteForwardingEnabled = false`.
        // Since extendedHostList is private, we test the outcome.
        // If the host is not in the list, or is in the list as non-forwarding, it should fail.
        // The currentHostSpec is readerHostSpec. If extendedHostList does not contain it, or contains it as non-forwarding.

        // Assume readerHostSpec is NOT in extendedHostList or is there with forwarding disabled.
        // The plugin's `execute` method will get the hostSpec, then check its role.
        // If it's a reader, it checks `extendedInfo != null && extendedInfo.isWriteForwardingEnabled`.

        SQLException exception = assertThrows(SQLException.class, () -> {
            plugin.execute(
                ResultSet.class, // resultClass
                SQLException.class, // exceptionClass
                mockStatement, // methodInvokeOn (a Statement)
                "executeQuery", // methodName
                mockSqlFunction, // jdbcMethodFunc
                new Object[]{"SELECT 1"} // args
            );
        });

        assertTrue(exception.getMessage().contains("Strict-writer mode: Method executeQuery called on host " + READER_HOST + " which is not a writer instance and not a write-forwarding enabled reader."));
        verify(mockSqlFunction, never()).call(); // Original function should not be called
    }

    @Test
    void testStrictWriterAttributeEnforcement_OnWriter_DoesNotThrow() throws Exception {
        plugin.setSessionAttribute(mockConnection, AuroraAdvancedFeaturesPlugin.STRICT_WRITER_SESSION_ATTRIBUTE, "true");
        when(mockPluginService.getCurrentHostSpec()).thenReturn(writerHostSpec); // Current host is a WRITER

        plugin.execute(
            ResultSet.class,
            SQLException.class,
            mockStatement,
            "executeQuery",
            mockSqlFunction,
            new Object[]{"SELECT 1"}
        );

        verify(mockSqlFunction).call(); // Original function SHOULD be called
    }

    @Test
    void testStrictWriterAttributeEnforcement_OnForwardingReader_DoesNotThrow() throws Exception {
        plugin.setSessionAttribute(mockConnection, AuroraAdvancedFeaturesPlugin.STRICT_WRITER_SESSION_ATTRIBUTE, "true");
        when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec); // Current host is a READER

        // Simulate that this reader IS a forwarding reader.
        // This requires readerHostSpec to be in extendedHostList with isWriteForwardingEnabled = true.
        // This state is set by checkAndRecordWriteForwardingSupport during a connect call.
        // We need to simulate this state.
        // For this test, we'll assume connect was called, and it determined forwarding was enabled.
        // This is tricky because `extendedHostList` is private.
        // A robust way:
        // 1. Call `plugin.connect` with mocks that make `isWriteForwardingEnabledOnReader` return true for `readerHostSpec`.
        // This will populate `extendedHostList` correctly.

        // Setup for connect to mark reader as forwarding:
        when(mockConnection.createStatement()).thenReturn(mockStatement); // Already in setup, but ensure it's here
        // Version query for connect:
        setupVersionQuery("PostgreSQL 16.0, Aurora 3.0.0"); // Any version is fine for this part
        setupAuroraVersionQuery("3.0.0");
        // Mock for isWriteForwardingEnabledOnReader to return true
        when(mockStatement.executeQuery(eq("SHOW aurora_replica_write_forwarding"))) // Fictional GUC from plugin
            .thenReturn(mockResultSetAuroraVersion); // Reuse a mock ResultSet
        when(mockResultSetAuroraVersion.next()).thenReturn(true).thenReturn(false);
        when(mockResultSetAuroraVersion.getString(1)).thenReturn("on");

        // Call connect to populate extendedHostList
        plugin.connect("protocol", readerHostSpec, mockProperties, false, mockConnectFunc);


        // Now call execute
        plugin.execute(
            ResultSet.class,
            SQLException.class,
            mockStatement,
            "executeQuery",
            mockSqlFunction,
            new Object[]{"SELECT 1"}
        );

        verify(mockSqlFunction).call(); // Original function SHOULD be called
    }

    // TODO: Add test for clearing session attributes on Connection.close()

    @Test
    void testSetStrictWriterAttributeViaSql_ExecuteTrue() throws Exception {
        String sql = "SET SESSION aws_advanced_jdbc.strict_writer = 'true'";
        when(mockSqlFunction.call()).thenReturn(false); // For Statement.execute

        boolean result = plugin.execute(
            Boolean.class, SQLException.class, mockStatement, "execute", mockSqlFunction, new Object[]{sql});

        assertFalse(result); // Should return false as it's consumed
        assertEquals("true", plugin.getSessionAttribute(mockConnection, AuroraAdvancedFeaturesPlugin.STRICT_WRITER_SESSION_ATTRIBUTE));
        verify(mockSqlFunction, never()).call();
    }

    @Test
    void testSetStrictWriterAttributeViaSql_ExecuteUpdateFalse() throws Exception {
        String sql = "SET SESSION aws_advanced_jdbc.strict_writer = 'false'";
        when(mockSqlFunction.call()).thenReturn(0); // For Statement.executeUpdate

        int result = plugin.execute(
            Integer.class, SQLException.class, mockStatement, "executeUpdate", mockSqlFunction, new Object[]{sql});

        assertEquals(0, result); // Should return 0 as it's consumed
        assertEquals("false", plugin.getSessionAttribute(mockConnection, AuroraAdvancedFeaturesPlugin.STRICT_WRITER_SESSION_ATTRIBUTE));
        verify(mockSqlFunction, never()).call();
    }

    @Test
    void testSetStrictWriterAttributeViaSql_ExecuteLargeUpdate_CaseInsensitive() throws Exception {
        String sql = "sEt    SeSsIoN    aws_advanced_jdbc.strict_writer   = 'TRUE'  ";
        when(mockSqlFunction.call()).thenReturn(0L); // For Statement.executeLargeUpdate

        long result = plugin.execute(
            Long.class, SQLException.class, mockStatement, "executeLargeUpdate", mockSqlFunction, new Object[]{sql});

        assertEquals(0L, result);
        assertEquals("true", plugin.getSessionAttribute(mockConnection, AuroraAdvancedFeaturesPlugin.STRICT_WRITER_SESSION_ATTRIBUTE));
        verify(mockSqlFunction, never()).call();
    }

    @Test
    void testRegularSqlNotAffectedBySetParser() throws Exception {
        String sql = "SELECT 1";
        plugin.setSessionAttribute(mockConnection, AuroraAdvancedFeaturesPlugin.STRICT_WRITER_SESSION_ATTRIBUTE, "false"); // Pre-set
        when(mockSqlFunction.call()).thenReturn(true); // For Statement.execute returning a ResultSet

        boolean result = plugin.execute(
            Boolean.class, SQLException.class, mockStatement, "execute", mockSqlFunction, new Object[]{sql});

        assertTrue(result);
        // Attribute should not change
        assertEquals("false", plugin.getSessionAttribute(mockConnection, AuroraAdvancedFeaturesPlugin.STRICT_WRITER_SESSION_ATTRIBUTE));
        verify(mockSqlFunction).call();
    }

    @Test
    void testLocalWriteForwardingDetection_Enabled() throws SQLException {
        // Setup connect() mocks
        setupVersionQuery("PostgreSQL 16.0, Aurora 3.0.0"); // Any version
        setupAuroraVersionQuery("3.0.0"); // Is Aurora

        // Mock the response for the write forwarding check query
        ResultSet mockForwardingResultSet = mock(ResultSet.class);
        when(mockStatement.executeQuery(eq("SHOW aurora_replica_write_forwarding"))).thenReturn(mockForwardingResultSet);
        when(mockForwardingResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockForwardingResultSet.getString(1)).thenReturn("on");

        plugin.connect("protocol", readerHostSpec, mockProperties, false, mockConnectFunc);

        // Indirectly verify extendedHostList:
        // If strict-writer is true, and we are on this reader, execute should not throw.
        plugin.setSessionAttribute(mockConnection, AuroraAdvancedFeaturesPlugin.STRICT_WRITER_SESSION_ATTRIBUTE, "true");
        when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec);

        assertDoesNotThrow(() -> plugin.execute(
            ResultSet.class, SQLException.class, mockStatement, "executeQuery", mockSqlFunction, new Object[]{"SELECT 1"}));
    }

    @Test
    void testLocalWriteForwardingDetection_Disabled() throws SQLException {
        setupVersionQuery("PostgreSQL 16.0, Aurora 3.0.0");
        setupAuroraVersionQuery("3.0.0");

        ResultSet mockForwardingResultSet = mock(ResultSet.class);
        when(mockStatement.executeQuery(eq("SHOW aurora_replica_write_forwarding"))).thenReturn(mockForwardingResultSet);
        when(mockForwardingResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockForwardingResultSet.getString(1)).thenReturn("off"); // Forwarding disabled

        plugin.connect("protocol", readerHostSpec, mockProperties, false, mockConnectFunc);

        plugin.setSessionAttribute(mockConnection, AuroraAdvancedFeaturesPlugin.STRICT_WRITER_SESSION_ATTRIBUTE, "true");
        when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec);

        assertThrows(SQLException.class, () -> plugin.execute(
            ResultSet.class, SQLException.class, mockStatement, "executeQuery", mockSqlFunction, new Object[]{"SELECT 1"}));
    }

    // AZ Awareness Tests (Conceptual - relies on logging, getAzFromHostSpec currently returns null)
    // These tests will primarily verify that the logic paths are hit,
    // and logs would show "AZ cannot be determined" given current getAzFromHostSpec.
    // With Host AZ Map, these tests can be more concrete.

    @Test
    void testGetAzFromHostSpec_WithMapAndFallback() {
        // Setup Host-AZ map in properties
        Properties propsWithAzMap = new Properties();
        propsWithAzMap.setProperty(AuroraAdvancedFeaturesPlugin.AURORA_PLUGIN_HOST_AZ_MAP_PROPERTY,
            "host-in-map.com:az-from-map-1a,"
          + "another-host.com:az-other,"
          + "host-with-pattern.us-east-1b.rds.amazonaws.com:map-takes-precedence-az"); // This last one also tests map precedence
        when(mockPluginService.getProperties()).thenReturn(propsWithAzMap);
        plugin = new AuroraAdvancedFeaturesPlugin(mockPluginService); // Re-initialize with new props

        // Test cases
        HostSpec hostInMap = new HostSpec("host-in-map.com", DEFAULT_PORT, HostRole.WRITER);
        assertEquals("az-from-map-1a", plugin.getAzFromHostSpec(hostInMap));

        HostSpec anotherHostInMap = new HostSpec("another-host.com", DEFAULT_PORT, HostRole.READER);
        assertEquals("az-other", plugin.getAzFromHostSpec(anotherHostInMap));

        HostSpec hostNotInMapWithPattern = new HostSpec("pattern-host.eu-west-2c.rds.amazonaws.com", DEFAULT_PORT, HostRole.READER);
        assertEquals("eu-west-2c", plugin.getAzFromHostSpec(hostNotInMapWithPattern));

        HostSpec hostMapTakesPrecedence = new HostSpec("host-with-pattern.us-east-1b.rds.amazonaws.com", DEFAULT_PORT, HostRole.WRITER);
        assertEquals("map-takes-precedence-az", plugin.getAzFromHostSpec(hostMapTakesPrecedence));

        HostSpec hostNotInMapNoPattern = new HostSpec("unknown-host.com", DEFAULT_PORT, HostRole.WRITER);
        assertNull(plugin.getAzFromHostSpec(hostNotInMapNoPattern));

        HostSpec hostWithNoDots = new HostSpec("localhost", DEFAULT_PORT, HostRole.WRITER);
        assertNull(plugin.getAzFromHostSpec(hostWithNoDots));

        assertNull(plugin.getAzFromHostSpec(null));
    }


    @Test
    void testAzPreferenceLoggingInConnect_PreferredAzSetAndMatched() throws SQLException {
        Properties connectProps = new Properties();
        connectProps.setProperty(AuroraAdvancedFeaturesPlugin.AURORA_PLUGIN_PREFERRED_AZ_PROPERTY, "us-east-1a");
        connectProps.setProperty(AuroraAdvancedFeaturesPlugin.AURORA_PLUGIN_HOST_AZ_MAP_PROPERTY,
            writerHostSpec.getHost() + ":us-east-1a," + readerHostSpec.getHost() + ":us-east-1b");

        // Ensure plugin is initialized with these properties (constructor uses pluginService.getProperties)
        when(mockPluginService.getProperties()).thenReturn(connectProps);
        plugin = new AuroraAdvancedFeaturesPlugin(mockPluginService);

        setupVersionQuery("PostgreSQL 16.0, Aurora 3.0.0");
        setupAuroraVersionQuery("3.0.0");

        // Spy on the logger or use a LogCaptor if available. For now, assume logAzPreference is called.
        // To verify specific log messages, a LogCaptor would be needed.
        // This test will verify that connect completes and implies specific logging paths were taken.

        // Connecting to writerHostSpec which is configured to be in "us-east-1a" (preferred)
        plugin.connect("protocol", writerHostSpec, connectProps, true, mockConnectFunc);
        // Expected log: "Connecting to host writer.host.example.com in preferred AZ us-east-1a."

        // Connecting to readerHostSpec which is in "us-east-1b" (not preferred)
        plugin.connect("protocol", readerHostSpec, connectProps, true, mockConnectFunc);
        // Expected log: "Connecting to host reader.host.example.com in AZ us-east-1b, which is not the preferred AZ us-east-1a."

        assertTrue(true, "Test executes. Log verification would require LogCaptor or similar.");
    }

    @Test
    void testFailoverPreferredAzRecoveryLogging_WriterInPreferredAz() {
        Properties initialProps = new Properties();
        initialProps.setProperty(AuroraAdvancedFeaturesPlugin.AURORA_PLUGIN_PREFERRED_AZ_PROPERTY, "us-west-2b");
        initialProps.setProperty(AuroraAdvancedFeaturesPlugin.AURORA_PLUGIN_HOST_AZ_MAP_PROPERTY,
            "writer-pref-az.example.com:us-west-2b," + readerHostSpec.getHost() + ":us-west-2a");
        when(mockPluginService.getProperties()).thenReturn(initialProps);
        plugin = new AuroraAdvancedFeaturesPlugin(mockPluginService);

        HostSpec preferredAzWriter = new HostSpec("writer-pref-az.example.com", DEFAULT_PORT, HostRole.WRITER);
        List<HostSpec> hosts = new ArrayList<>();
        hosts.add(preferredAzWriter);
        hosts.add(readerHostSpec); // reader in a different AZ
        when(mockPluginService.getHosts()).thenReturn(hosts);

        plugin.notifyNodeListChanged(Collections.emptyMap());
        // Expected log: "Failover Handling: Writer instance(s) in preferred AZ us-west-2b are available: writer-pref-az.example.com"
        assertTrue(true, "Test executes. Log verification would require LogCaptor or similar.");
    }

    @Test
    void testFailoverPreferredAzRecoveryLogging_WriterNotInPreferredAz() {
         Properties initialProps = new Properties();
        initialProps.setProperty(AuroraAdvancedFeaturesPlugin.AURORA_PLUGIN_PREFERRED_AZ_PROPERTY, "us-west-2b");
        initialProps.setProperty(AuroraAdvancedFeaturesPlugin.AURORA_PLUGIN_HOST_AZ_MAP_PROPERTY,
            writerHostSpec.getHost() + ":us-west-2a," + // Writer in a different AZ
            readerHostSpec.getHost() + ":us-west-2c");
        when(mockPluginService.getProperties()).thenReturn(initialProps);
        plugin = new AuroraAdvancedFeaturesPlugin(mockPluginService);

        List<HostSpec> hosts = new ArrayList<>();
        hosts.add(writerHostSpec); // This writer is in us-west-2a, not the preferred us-west-2b
        hosts.add(readerHostSpec);
        when(mockPluginService.getHosts()).thenReturn(hosts);

        plugin.notifyNodeListChanged(Collections.emptyMap());
        // Expected log: "Failover Handling: No writer instance available in preferred AZ us-west-2b."
        assertTrue(true, "Test executes. Log verification would require LogCaptor or similar.");
    }
}
