plugins {
    java
}

group = "com.hobbang"
version = "1.4.4"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", project.version)
    filesMatching("paper-plugin.yml") { expand("version" to project.version) }
}

tasks.jar {
    archiveFileName.set("hobbang-enhance-paper-${project.version}.jar")
}

val apiSources by configurations.creating
dependencies { apiSources("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT:sources") }
tasks.register<Copy>("fetchApiSources") { from(apiSources); into(layout.buildDirectory.dir("apisrc")) }
