plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.dxlabs.dxotimiza"
    compileSdk = 36

    defaultConfig {
        // Pacote mantido da v0.1 de propósito: o push via API não apaga
        // arquivos, então trocar o pacote deixaria as classes antigas
        // órfãs no repo. O nome exibido muda para JPG Otimiza em
        // strings.xml; o pacote só mudaria numa v1 para a Play.
        applicationId = "br.com.dxlabs.dxotimiza"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "2.0.0"
        // v0.4: sem NDK. O MozJPEG foi removido (lento na Tab) e o
        // app voltou ao encoder Android da v0.1. O workflow ainda
        // instala NDK/CMake, sem uso — limpeza opcional, não obrigatória.
    }

    // Assinatura de release para a Play (mesmo padrão do Teleprompter):
    // só ativa quando o workflow passa KEYSTORE_PATH e as senhas por
    // variáveis de ambiente. Sem elas, o build de debug não muda nada
    // e o bundleRelease fica sem assinatura.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storeType = "PKCS12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
