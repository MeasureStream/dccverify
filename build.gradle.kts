plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "it.polito"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

// Implementation delle depndencies dice quali "librerie" scaricarsi a java (dipendenze), come pip install per python

dependencies {
    // --- da Spring Initializr ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // --- firma XML (Apache Santuario) ---
    implementation("org.apache.santuario:xmlsec:3.0.3")

    // --- firma PDF (iText 8) ---
    implementation("com.itextpdf:kernel:8.0.2")
    implementation("com.itextpdf:sign:8.0.2")
    implementation("com.itextpdf:bouncy-castle-adapter:8.0.2")

    // --- crittografia (BouncyCastle) ---
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.77")

	//Questi sono i requirements solo per i test

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
