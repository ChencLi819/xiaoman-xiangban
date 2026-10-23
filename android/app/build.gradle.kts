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
        versionCode = 15
        versionName = "1.5.1"
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

    signingConfigs {
        /* 两把钥匙并存，由 android/gradle.properties 的 xiaoman.signing 选择（默认 legacy）：
           - legacy ：事故前那把旧钥匙（cert 794aca5a…），签过 v1.4.8 / v1.5.0。
                      两台手机上现存的版本都是它 —— 换机后要「接上」旧版本，必须用它签。
           - xiaoman：2026-10-23 误以为旧钥匙丢失时新建的钥匙（cert 552b01a2…），
                      只签过 v1.5.1；仅当手机已经装过 v1.5.1 事故包时才选它。
           两把都是 androiddebugkey/android，密钥文件随仓库分发，任何机器构建同签名。
           更多见 docs/signing-keys.md。 */
        create("legacy") {
            storeFile = file("xiaoman-signing-legacy.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("xiaoman") {
            storeFile = file("xiaoman-signing.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    /* 选择用哪把钥匙签名：legacy / xiaoman */
    val signingChoice = (findProperty("xiaoman.signing") as String?)?.trim().orEmpty().ifEmpty { "legacy" }
    require(signingChoice == "legacy" || signingChoice == "xiaoman") {
        "xiaoman.signing 只能是 legacy 或 xiaoman，当前为「$signingChoice」"
    }

    buildTypes {
        /* debug 与 release 共用 applicationId 与签名 ——
           正式版可以直接覆盖安装 debug 版，数据目录不变、Room 走迁移，历史数据原地保留。
           ⚠️ 不要给 debug 加 applicationIdSuffix：包名一变就是另一个 App，数据目录也跟着变，
           切换版本会表现为「数据全没了」。换签名的迁移方案见 docs/migration.md。 */
        debug { signingConfig = signingConfigs.getByName(signingChoice) }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(signingChoice)
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
