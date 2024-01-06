plugins {
    kotlin("jvm") version "1.5.21"
    `maven-publish`
    id("org.jetbrains.kotlinx.kover") version "0.7.4"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.2")

    implementation("org.springframework:spring-core:5.3.8")
    implementation("org.springframework:spring-context:5.3.8")

    implementation(platform("com.fasterxml.jackson:jackson-bom:2.12.0"))
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation(platform("org.junit:junit-bom:5.9.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:1.12.3")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("ch.qos.logback:logback-classic:1.2.12")
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    build {
        dependsOn(koverVerify)
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/olivergergelyszabo/navpollinglibrary")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        /*register<MavenPublication>("release") {
            groupId = "com.github.OliverGergelySzabo"
            artifactId = "navPollingLibrary"
            from(components["kotlin"])
        }*/

        register<MavenPublication>("gpr") {
            from(components["kotlin"])
        }
    }
}

kover {
    excludeInstrumentation {
        // these are needed otherwise some test fail due to instrumentation creating field
        // for classes which are then scanned for declared fields
    classes(
        "com.github.oliverszabo.navpolling.eventpublishing.EventPublisherTest$*",
        "com.github.oliverszabo.navpolling.integration.TargetInvoiceClass"
    )
        packages("com.github.oliverszabo.navpolling.model")
    }
}

koverReport {
    filters {
        excludes {
            packages("com.github.oliverszabo.navpolling.model", "com.github.oliverszabo.navpolling.util")
            classes("com.github.oliverszabo.navpolling.polling.dto.QueryInvoiceDigestRequest")
        }
    }

    verify {
        rule {
            isEnabled = true
            filters {
                excludes {
                    packages("com.github.oliverszabo.navpolling.model", "com.github.oliverszabo.navpolling.util")
                    classes("com.github.oliverszabo.navpolling.polling.dto.QueryInvoiceDigestRequest")
                }
            }
            minBound(95)
        }
    }
}