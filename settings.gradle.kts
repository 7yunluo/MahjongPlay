rootProject.name = "MahjongPlay"

pluginManagement {
    repositories {
        maven {
            name = "GradlePluginPortalMirror"
            url = uri("https://plugins.gradle.org/m2")
        }
        maven {
            name = "AliyunMavenCentral"
            url = uri("https://maven.aliyun.com/repository/central")
        }
        maven {
            name = "GoogleMavenCentral"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        gradlePluginPortal()
    }
}
