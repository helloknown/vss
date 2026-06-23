plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdeaCommunity("2024.3.5")
        bundledModule("intellij.platform.vcs.impl")
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))
        }
        resources {
            setSrcDirs(listOf("resources"))
        }
    }
    test {
        java {
            setSrcDirs(listOf("testSource"))
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "SourceSafe"
        name = "Visual SourceSafe Integration"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "241"
            // No upper bound: compatible with IDEA 2026.1+ until a breaking platform API change occurs.
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
    instrumentCode = true
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    test {
        useJUnit()
    }
}
