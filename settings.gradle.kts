pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "school-report-system"
include(":core", ":pdf", ":desktop-admin", ":desktop-teacher", ":teacher-app")
