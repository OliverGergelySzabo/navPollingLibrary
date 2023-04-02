plugins {
    kotlin("jvm") version "1.5.20"
    `maven-publish`
}

group = "com.github.oliverszabo"
version = "1.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.springframework:spring-core:5.3.8")
    implementation("org.springframework:spring-context:5.3.8")
    implementation("org.slf4j:slf4j-api:1.7.31")

    testImplementation(platform("org.junit:junit-bom:5.9.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:1.12.3")
}

tasks {
    /*withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "11"
            languageVersion = "1.5"
        }
    }*/
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.OliverGergelySzabo"
            artifactId = "navPollingLibrary"
            from(components["kotlin"])
        }
    }
}