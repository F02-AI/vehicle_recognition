// Top-level build file where you can add configuration options common to all sub-projects/modules
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("org.jetbrains.kotlin.multiplatform") version "1.9.10" apply false
    id("com.google.devtools.ksp") version "1.9.10-1.0.13" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

// Task to help identify and fix Gradle deprecation warnings
tasks.register("identifyDeprecations") {
    description = "Runs a build with --warning-mode all to identify deprecation warnings"
    
    doLast {
        println("\n\n=== RECOMMENDATIONS FOR FIXING GRADLE DEPRECATION WARNINGS ===\n")
        println("1. Mutating configurations after they've been resolved:")
        println("   - Review all plugin applications to ensure they're applied early enough")
        println("   - Fix 'Convention' type deprecation by updating to latest Android Gradle Plugin")
        println("   - Configure dependencies before task execution phase")
        println("\n2. For BuildIdentifier.getName() deprecation:")
        println("   - Wait for the Kotlin Multiplatform plugin to update")
        println("   - This is an internal implementation detail of the plugin")
        println("\n3. For ReportingExtension.getBaseDir() deprecation:")
        println("   - Update to use getBaseDirectory() property method instead")
        println("\n4. Ensure all plugins are using compatible versions")
        println("\nFor more information, visit: https://docs.gradle.org/8.12/userguide/upgrading_version_8.html")
    }
}
