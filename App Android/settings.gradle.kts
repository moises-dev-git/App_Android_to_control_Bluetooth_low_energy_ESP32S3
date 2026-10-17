pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Captura UVC (placa HDMI -> USB): com.github.ernestp.AndroidUSBCamera
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "ESP32 App"
include(":app")
