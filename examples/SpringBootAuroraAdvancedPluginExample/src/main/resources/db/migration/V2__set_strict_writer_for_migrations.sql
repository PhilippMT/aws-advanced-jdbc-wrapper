-- Attempt to set the strict_writer session attribute for this Flyway migration transaction.
-- The effectiveness of this depends on:
-- 1. If `SET SESSION` is a command that the AuroraAdvancedFeaturesPlugin can intercept via its `execute` method
--    when Statement.execute() is called by Flyway.
-- 2. If the session state set by `SET SESSION` is visible to the plugin when it later checks attributes.
--
-- An alternative would be to configure connection properties for Flyway's DataSource (if possible through Spring Boot)
-- to pass `aws_advanced_jdbc.strict_writer=true` as a connection property, which the plugin
-- could read during the `connect` phase.
--
-- For now, this script demonstrates the intent. The plugin's `execute` method should be
-- enhanced to recognize `SET SESSION aws_advanced_jdbc.strict_writer` and store it appropriately
-- in its `connectionSessionAttributes` map.

-- Try to ensure this session uses the writer.
-- This specific command `SET SESSION aws_advanced_jdbc.strict_writer = 'true'` is hypothetical
-- for how the plugin would be directly controlled via SQL.
-- The plugin currently expects this to be set via its Java API setSessionAttribute()
-- or potentially a connection property.
-- For Flyway, if we want it to run in "strict-writer" mode, we'd ideally pass a connection
-- property that the plugin picks up when Flyway establishes its connection.
--
-- Let's assume for this example, this SQL is more of a marker or a test case.
-- The actual enforcement by the plugin will happen on subsequent DML/DDL if the attribute
-- is set on the connection Flyway is using.

-- If the plugin were to directly interpret this SQL:
-- SET SESSION property_key = 'property_value'; -- This is a general pattern.
-- Our plugin attribute is 'strict-writer'.
-- The plugin's `execute` method would need to parse this specific SQL pattern if we want SQL to control it.
-- Currently, `CustomerService.setSessionStrictWriter()` uses `jdbcTemplate.execute("SET SESSION ...")`.
-- The plugin's `execute` method intercepts `Statement.execute(...)`. If that `SET SESSION`
-- command is executed through such a path, the plugin *could* try to parse it.
-- However, the plugin's `setSessionAttribute` Java method is a more direct way to control its state.

-- This statement is more of a placeholder for the concept.
-- For Flyway to *actually* run its migrations with strict-writer mode enforced by the plugin,
-- the connection Flyway uses must have the 'strict-writer'='true' attribute set in
-- the plugin's `connectionSessionAttributes` map.
-- This could be achieved if Flyway's connection goes through the plugin's `connect` method
-- and some property like `defaultStrictWriter=true` is passed, or if an initial SQL string
-- like `SET aws_advanced_jdbc.strict_writer=true` is configured for Hikari/DataSource
-- and the plugin's `execute` method handles it.

-- For now, this file serves as a test that Flyway runs this script.
-- The actual strict-writer enforcement for Flyway's DDL would depend on how the plugin
-- is made aware of the "strict-writer" requirement for Flyway's connections.

-- Inserting initial data, which should go to the writer if strict-writer mode is active
-- and correctly enforced for Flyway's connection.
INSERT INTO customers (name) VALUES ('Flyway Initial Customer - From V2');

-- Example of a DDL statement that should also go to the writer
ALTER TABLE customers ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- After operations, optionally turn it off if it was only for this script,
-- though Flyway runs each script in its own transaction/session usually.
-- SET SESSION aws_advanced_jdbc.strict_writer = 'false';
