plugins {
    java
    application
}

group = "edu.northeastern.cs6650"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("io.github.cdimascio:java-dotenv:3.2.0")
}

application {
    mainClass.set("edu.northeastern.cs6650.client.Client")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "edu.northeastern.cs6650.client.Client"
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}