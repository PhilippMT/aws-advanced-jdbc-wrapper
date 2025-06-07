# Using the Aurora Advanced Features Plugin

The `AuroraAdvancedFeaturesPlugin` provides enhanced functionalities specifically tailored for AWS Aurora PostgreSQL clusters. It aims to simplify development by providing intelligent connection handling, failover awareness, and fine-grained control over session behavior.

## Features

*   **Aurora PostgreSQL 17+ Compatibility:** Ensures smooth operation with the latest Aurora PostgreSQL versions.
*   **Local Write Forwarding Awareness (Conceptual):** The plugin can identify reader instances that are configured for write forwarding. While full write redirection is not yet implemented, this lays the groundwork for future enhancements. (Note: The current mechanism to detect this relies on a placeholder query `SHOW aurora_replica_write_forwarding` and needs to be updated based on actual Aurora capabilities).
*   **Topology-Aware Connection Logic (AZ Awareness):**
    *   Prioritizes connections to database instances within the same Availability Zone (AZ) as the application, if configured.
    *   Requires configuration of the application's preferred AZ and a mapping of instance hostnames to their AZs.
*   **Failover Awareness:**
    *   Monitors changes in the cluster topology.
    *   Logs information about the availability of writer instances in the preferred AZ, especially after failover events, aiding in diagnostics.
*   **"Strict-Writer" Session Mode:**
    *   Ensures that database operations for a given session are only executed on a true writer instance or a write-forwarding reader.
    *   If enabled and the current connection is to a non-forwarding reader, operations that require write access will be blocked by the plugin, throwing an SQLException.
    *   This is particularly useful for ensuring data consistency for critical operations or during schema migrations.

## Loading the Plugin

To use the Aurora Advanced Features Plugin, include `auroraAdvancedFeatures` in the `wrapperPlugins` connection parameter.

Example using connection properties:
```java
Properties props = new Properties();
props.setProperty("user", "your_user");
props.setProperty("password", "your_password");
props.setProperty("wrapperPlugins", "auroraAdvancedFeatures"); // Add other plugins as needed

// Example: If also using failover and host monitoring (efm)
// props.setProperty("wrapperPlugins", "auroraAdvancedFeatures,failover,efm");
// Ensure 'auroraAdvancedFeatures' is early in the chain if it needs to influence or observe initial connection properties.

Connection conn = DriverManager.getConnection("jdbc:aws-wrapper:postgresql://your-aurora-cluster-endpoint/your_db", props);
```

Example for Spring Boot `application.properties` with HikariCP:
```properties
spring.datasource.url=jdbc:aws-wrapper:postgresql://your-aurora-cluster-endpoint/your_db
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.datasource.driver-class-name=software.amazon.jdbc.Driver

# Enable the plugin
spring.datasource.hikari.data-source-properties.wrapperPlugins=auroraAdvancedFeatures

# Configure plugin-specific properties (see below)
spring.datasource.hikari.data-source-properties.auroraPluginPreferredAz=us-east-1a
spring.datasource.hikari.data-source-properties.auroraPluginHostAzMap=instance1.host:us-east-1a,instance2.host:us-east-1b
```

## Configuration Parameters

The plugin uses the following optional configuration parameters, typically provided via connection properties or datasource properties:

*   **`auroraPluginPreferredAz`** (String):
    *   Specifies the preferred Availability Zone for the application. The plugin will log information regarding instance availability in this AZ.
    *   Example: `us-east-1a`
*   **`auroraPluginHostAzMap`** (String):
    *   A comma-separated list of instance-host-to-AZ mappings.
    *   Format: `host1:az1,host2:az2,...`
    *   This map is used by the plugin to determine the AZ of each instance in the cluster for its topology-aware logging.
    *   Example: `my-instance-1.xxxx.region.rds.amazonaws.com:us-east-1a,my-instance-2.xxxx.region.rds.amazonaws.com:us-east-1b`

## Controlling "Strict-Writer" Mode

The "strict-writer" mode can be controlled at runtime for each connection using a special `SET SESSION` command. The plugin intercepts this command and applies the mode change internally.

*   **Enable Strict-Writer Mode:**
    ```sql
    SET SESSION aws_advanced_jdbc.strict_writer = 'true';
    ```
*   **Disable Strict-Writer Mode:**
    ```sql
    SET SESSION aws_advanced_jdbc.strict_writer = 'false';
    ```

**Usage with Flyway:**
To ensure Flyway migrations run with strict-writer mode enabled (recommended to prevent accidental writes to a reader during schema changes), you can include the `SET` command at the beginning of your migration scripts or in a `beforeMigrate.sql` callback script.

Example (`V2__enable_strict_writer_and_migrate.sql`):
```sql
SET SESSION aws_advanced_jdbc.strict_writer = 'true';

-- Your migration DDL/DML statements here
CREATE TABLE my_new_table (...);
INSERT INTO my_new_table VALUES (...);
```
If Flyway attempts to execute these migrations on a connection that the plugin identifies as a non-forwarding reader while strict-writer mode is active, the plugin will throw an exception, failing the migration and preventing potential issues.

## Example Application

For a practical example of how to configure and use the `AuroraAdvancedFeaturesPlugin` in a Spring Boot 3 application with Hibernate and Flyway, please refer to the `SpringBootAuroraAdvancedPluginExample` located in the `examples` directory of this project. This example demonstrates:
*   Plugin configuration.
*   Usage of the "strict-writer" mode with Flyway.
*   Basic CRUD operations with the plugin enabled.

## Limitations

*   **Local Write Forwarding:** The detection of write-forwarding capabilities on reader instances currently relies on a placeholder mechanism (`SHOW aurora_replica_write_forwarding`). This needs to be updated with the actual method provided by Aurora PostgreSQL for checking this status. The plugin does not yet automatically redirect writes if a reader is write-forwarding capable; it only uses this information for the "strict-writer" mode decision.
*   **Availability Zone Determination:** While the `auroraPluginHostAzMap` allows for explicit AZ configuration, the fallback heuristic of parsing hostnames for AZ information might not be universally reliable for all RDS endpoint naming schemes.
```
