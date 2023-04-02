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
}

/*tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "11"
            languageVersion = "1.5"
        }
    }
}*/

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.OliverGergelySzabo"
            artifactId = "navPollingLibrary"
            from(components["kotlin"])
        }
    }
}