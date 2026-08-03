// Top-level build file. Plugins are declared here and applied in :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// google-services is resolved here but applied conditionally in :app (only when
// a google-services.json exists), so a checkout with no Firebase project still
// builds. Declared with `apply false` so the classpath is ready either way.
buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.2")
    }
}
