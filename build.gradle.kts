
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.jooq.monosoul)
    alias(libs.plugins.ktlint)
}

group = "io.olkkani"
version = "0.0.1-SNAPSHOT"
description = "lol-view-back"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.common)
    implementation(libs.bundles.spring) {
        exclude(
            group = "org.springframework.boot",
            module = "spring-boot-starter-tomcat",
        )
    }
    implementation(libs.bundles.spring.security)
    implementation(libs.bundles.jjwt)
    developmentOnly(libs.spring.devtools)
    testImplementation(libs.bundles.test)
    testImplementation(libs.bundles.spring.test)

    implementation(libs.bundles.kotlin.coroutines)
    testImplementation(libs.bundles.kotlin.coroutines.test)

    implementation(libs.bundles.persistence)
    implementation(libs.bundles.persistence.database)
    implementation(libs.p6spy)
    testImplementation(libs.bundles.persistence.test.testcontainer)
    developmentOnly(libs.bundles.persistence.database.embedded)
    jooqCodegen(libs.postgresql)
//    implementation("org.springframework.boot:spring-boot-starter-batch")
//    implementation("org.springframework.boot:spring-boot-starter-data-rest")
//    implementation("org.springframework.boot:spring-boot-starter-hateoas")
//    implementation("org.springframework.boot:spring-boot-starter-webclient")
//    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
//    developmentOnly("org.springframework.boot:spring-boot-docker-compose")


//    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
//    testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-data-rest-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-hateoas-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-webservices-test")
//    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
//    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
//    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val jooqGeneratedOutput = project.layout.buildDirectory.dir("generated-jooq") // 경로 변수화
sourceSets {
    main {
        kotlin.srcDirs(jooqGeneratedOutput)
    }
}
tasks.named("clean") {
    doLast {
        jooqGeneratedOutput.get().asFile.deleteRecursively()
    }
}
tasks {
    generateJooqClasses {
        schemas.set(listOf("public"))
        basePackageName.set("org.jooq.generated")
        migrationLocations.setFromFilesystem("src/main/resources/db/migration")
        outputDirectory.set(project.layout.buildDirectory.dir("generated-jooq"))
        flywayProperties.put("flyway.placeholderReplacement", "false")
        includeFlywayTable.set(true)
        outputSchemaToDefault.add("public")
        schemaToPackageMapping.put("public", "model")

        usingJavaConfig {
            /* "this" here is the org.jooq.meta.jaxb.Generator configure it as you please */
        }
    }
}

configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-simple")
}
