buildscript {
    repositories {
        google()
        // Add any other repositories you need here
    }
    dependencies {
        // Define the navigation version
        val nav_version = "2.7.7"
        // Add the Safe Args plugin classpath
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:$nav_version")

        // Add any other classpath dependencies you need here
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.10-1.0.13" apply false
    // Keep your existing plugins configuration
}