plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.velnox.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.velnox.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // The deployed Velnox API. Primary target is Cloudflare Workers (api.velnox.live),
        // with automatic fallback to workers.dev and legacy Render (velnox-api.onrender.com).
        // Overridable via gradle property -PvelnoxApiBaseUrl=...
        val apiBaseUrl = (project.findProperty("velnoxApiBaseUrl") as String?)
            ?.trim()
            ?.ifEmpty { null }
            ?: "https://api.velnox.live"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")

        val fallbackUrls = (project.findProperty("velnoxFallbackUrls") as String?)
            ?.trim()
            ?.ifEmpty { null }
            ?: "https://velnox-api.ma0979354966-js91934.workers.dev,https://velnox-api.onrender.com"
        buildConfigField("String", "API_FALLBACK_URLS", "\"$fallbackUrls\"")

        val mediaBaseUrl = (project.findProperty("velnoxMediaBaseUrl") as String?)
            ?.trim()
            ?.ifEmpty { null }
            ?: "https://pub-75f6f26e59df431b854fe53caedb49b6.r2.dev"
        buildConfigField("String", "MEDIA_BASE_URL", "\"$mediaBaseUrl\"")

        // Google Sign-In needs the *Web* OAuth client id as its `serverClientId`,
        // because the ID token's `aud` must match what the backend verifies.
        // It is a public identifier, not a secret, but it is deployment
        // configuration: supply it with -PvelnoxGoogleClientId=… or use deployment default.
        val googleClientId = (project.findProperty("velnoxGoogleClientId") as String?)
            ?.trim()
            ?.ifEmpty { null }
            ?: "133324383928-de3hpaliq2t0ndjpo5c3q1h8amc46n0v.apps.googleusercontent.com"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleClientId\"")

        // Stripe Publishable Key for in-app client card / payments
        val stripePublishableKey = (project.findProperty("velnoxStripePublishableKey") as String?)
            ?.trim()
            ?.ifEmpty { null }
            ?: "pk_test_51UKCn2Kp4iwMdWLy3iqkWlCC1pFshtCtvzxix3NlhCFosPINXALHG9JHMSHVytkZf3gU0uA31ka7iCd9eXmYdcpc00fmasjSzN"
        buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"$stripePublishableKey\"")

        // LINE Login Channel ID
        val lineChannelId = (project.findProperty("velnoxLineChannelId") as String?)
            ?.trim()
            ?.ifEmpty { null }
            ?: "2012753443"
        buildConfigField("String", "LINE_CHANNEL_ID", "\"$lineChannelId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Networking. The only new runtime dependency in Part 3: the app had no HTTP
    // client at all, and JSON is read with the platform org.json parser.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Product images. The catalogue returns absolute URLs on the seller's own
    // storage (Cloudflare R2), so the app needs an async image loader that can
    // cancel off-screen requests inside a lazy grid. Coil is that loader; it is
    // not a design system and adds no UI.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Google Sign-In. Credential Manager is the currently supported flow;
    // GoogleSignInClient was deprecated. These three artifacts are Google's own
    // recommended combination for "Sign in with Google" with an ID token.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // The mockable android.jar on the unit-test classpath contains *stub*
    // org.json classes that throw "Stub!" when called. The catalogue parsing is
    // tested against real captured API bodies, so the tests need the real
    // implementation; it is test-only and never packaged into the APK.
    testImplementation("org.json:json:20240303")
}
