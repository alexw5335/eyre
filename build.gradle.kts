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



dependencies {
	implementation(platform("org.apache.tika:tika-bom:2.7.0"))
	implementation("org.apache.tika:tika-parsers-standard-package")
}



tasks.jar {
	manifest {
		attributes("Main-Class" to "eyre.MainKt")
	}

	duplicatesStrategy = DuplicatesStrategy.EXCLUDE

	from({ configurations.runtimeClasspath.get().map { zipTree(it) } })
}