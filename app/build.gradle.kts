plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.dxlabs.dxotimiza"
    compileSdk = 34

    defaultConfig {
        // Pacote mantido da v0.1 de propósito: o push via API não apaga
        // arquivos, então trocar o pacote deixaria as classes antigas
        // órfãs no repo. O nome exibido muda para JPG Otimiza em
        // strings.xml; o pacote só mudaria numa v1 para a Play.
        applicationId = "br.com.dxlabs.dxotimiza"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
        ndk {
            // Tab S9+ é arm64-v8a. Só essa ABI na v0.2, para o build
            // do MozJPEG ser um só e mais rápido.
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    ndkVersion = "27.2.12479018"

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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
