plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.xiaoman.memo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xiaoman.memo"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.4.8"
        vectorDrawables { useSupportLibrary = true }
    }

    /* 双端并行：local=本地单机版；sync=同步端（带配对 UI，云端通道 v2 接入）。
       APK 产物自动命名为 xiaoman-<flavor>-v<version>.apk */
    flavorDimensions += "mode"
    productFlavors {
        create("local") {
            dimension = "mode"
            buildConfigField("boolean", "SYNC_ENABLED", "false")
            resValue("string", "app_name", "小满")
        }
        create("sync") {
            dimension = "mode"
            applicationIdSuffix = ".sync"
            buildConfigField("boolean", "SYNC_ENABLED", "true")
            resValue("string", "app_name", "小满·相伴")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            /* 自用分发：用 debug 密钥签 release 包（可安装、可升级；换正式密钥前 versionCode 不回退即可） */
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            outputImpl.outputFileName = "xiaoman-${flavorName}-v${versionName}-${buildType.name}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    /* WebDAV 需要 MKCOL 等非标准方法，Android 自带 HttpURLConnection 的方法白名单
       会直接抛 ProtocolException，必须用 OkHttp 发任意方法请求 */
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
