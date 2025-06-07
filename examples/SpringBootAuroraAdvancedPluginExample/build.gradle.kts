plugins {
    java
    id("org.springframework.boot") version "3.2.0" // Specify a recent Spring Boot version
    id("io.spring.dependency-management") version "1.1.4"
}

group = "software.amazon.jdbc.example"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")

    // AWS Advanced JDBC Wrapper - replace with specific version as needed
    // For a project build, this might be: implementation(project(":wrapper")) if it's a multi-project build
    // For a standalone example, using a published version:
    implementation("software.amazon.jdbc:aws-advanced-jdbc-wrapper:latest.release") // Or a specific version like "2.4.0"

    runtimeOnly("org.postgresql:postgresql") // Standard PostgreSQL driver

    // Hibernate Core is usually a transitive dependency from spring-boot-starter-data-jpa
    // implementation("org.hibernate.orm:hibernate-core") // Explicitly if needed

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Spring Boot specific configuration
springBoot {
    mainClass.set("software.amazon.jdbc.example.springbootaurora.SpringBootAuroraAdvancedPluginExampleApplication")
}
