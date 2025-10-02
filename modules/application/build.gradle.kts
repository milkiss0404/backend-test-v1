tasks.jar {
    enabled = true
}

tasks.bootJar {
    enabled = false
}

dependencies {
    implementation(projects.modules.domain)
    // Only need Spring annotations (@Service) for this module
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage")
    }

    testImplementation("io.mockk:mockk:1.13.5")
}
