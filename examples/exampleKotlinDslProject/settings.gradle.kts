pluginManagement{
    repositories {
        mavenLocal()
        maven("https://plugins.gradle.org/m2/")
    }
    // The resolutionStrategy only needs to be configured for 
    // local plugin development, specifically when using the 
    // mavenLocal repository.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.protobuf") {
                useModule("com.google.protobuf:protobuf-gradle-plugin:${requested.version}")
            }
        }
    }
}
rootProject.name = "exampleProject"

