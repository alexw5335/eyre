plugins {
    kotlin("jvm") version "1.8.0"
}

group = "none"
version = "1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(18)
}