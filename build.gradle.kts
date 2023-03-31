plugins {
    kotlin("jvm") version "1.8.10"
}

group = "com.github.oliverszabo"
version = "1.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.springframework:spring-core:5.3.8")
    implementation("org.springframework:spring-context:5.3.8")
}