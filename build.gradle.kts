import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.jooq.monosoul)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "io.olkkani"
version = "0.0.1-SNAPSHOT"
description = "lol-view-back"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
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
}



allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}



configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-simple")
}


val jooqGeneratedOutput = project.layout.buildDirectory.dir("generated-jooq") // 경로 변수화
sourceSets {
    main {
        kotlin.srcDirs(jooqGeneratedOutput)
    }
}
detekt {
    buildUponDefaultConfig = true // preconfigure defaults
    allRules = false // do not activate every rule by default
}
tasks {
    withType<Test> {
        useJUnitPlatform()
    }
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
            // "this" here is the org.jooq.meta.jaxb.Generator configure it as you please
        }
    }
    named("clean") {
        doLast {
            delete(jooqGeneratedOutput)
        }
    }
    withType<Detekt>().configureEach {
        reports {
            checkstyle.required.set(true)
            html.required.set(true)
            sarif.required.set(true)
            markdown.required.set(true)
        }
    }
}
