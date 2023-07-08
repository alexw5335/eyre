plugins {
    kotlin("jvm") version "1.9.0"
}



group = "none"
version = "1.0"



repositories {
    mavenCentral()
}



kotlin {
    jvmToolchain(18)
}



tasks.jar {
	manifest {
		attributes("Main-Class" to "eyre.MainKt")
	}

	duplicatesStrategy = DuplicatesStrategy.EXCLUDE

	from({ configurations.runtimeClasspath.get().map { zipTree(it) } })
}