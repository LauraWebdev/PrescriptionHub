import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries.android)
}

val appVersionName: String = providers.gradleProperty("appVersionName").getOrElse("1.0")
val appVersionCode: Int = providers.gradleProperty("appVersionCode").map(String::toInt).getOrElse(1)

val releaseKeystorePath: String? = providers.gradleProperty("releaseKeystorePath").orNull
val releaseKeystorePassword: String? = providers.gradleProperty("releaseKeystorePassword").orNull
val releaseKeyAlias: String? = providers.gradleProperty("releaseKeyAlias").orNull
val releaseKeyPassword: String? = providers.gradleProperty("releaseKeyPassword").orNull
val releaseKeystoreType: String = providers.gradleProperty("releaseKeystoreType").getOrElse("PKCS12")
val hasReleaseSigningConfig = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "media.laura.prescriptionhub"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "media.laura.prescriptionhub"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = releaseKeystoreType
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.aboutlibraries.compose.m3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

fun runProcess(command: List<String>, outFile: File? = null, ignoreFailure: Boolean = false): Pair<Int, String> {
    val builder = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT)
    if (outFile != null) builder.redirectOutput(outFile)
    val process = builder.start()
    val captured = if (outFile == null) String(process.inputStream.readBytes()) else ""
    val exit = process.waitFor()
    if (exit != 0 && !ignoreFailure) {
        error("Command failed (exit $exit): ${command.joinToString(" ")}")
    }
    return exit to captured
}

fun resolveAdb(): File {
    val sdkDir = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { localProps ->
            Properties().apply { localProps.inputStream().use { load(it) } }.getProperty("sdk.dir")
        }
        ?: error(
            "Could not determine the Android SDK location. Set ANDROID_HOME/ANDROID_SDK_ROOT, " +
                "or make sure local.properties has sdk.dir set."
        )
    val exeName = if (System.getProperty("os.name").lowercase().contains("win")) "adb.exe" else "adb"
    val adb = File(sdkDir, "platform-tools/$exeName")
    if (!adb.exists()) error("Could not find adb at $adb.")
    return adb
}

fun resolveDeviceSerial(adb: File): String {
    (project.findProperty("device") as String?)?.let { return it }
    System.getenv("ANDROID_SERIAL")?.let { return it }

    val (_, out) = runProcess(listOf(adb.absolutePath, "devices"))
    val serials = out.lines().drop(1).mapNotNull { line ->
        line.substringBefore('\t').trim().takeIf { it.isNotEmpty() && line.contains("\tdevice") }
    }
    return when (serials.size) {
        0 -> error("No connected adb devices/emulators found. Start an emulator or connect a device first.")
        1 -> serials.single()
        else -> error(
            "Multiple devices connected: ${serials.joinToString()}. " +
                "Pick one with -Pdevice=<serial>, or set ANDROID_SERIAL."
        )
    }
}

tasks.register("seedDebugDatabase") {
    group = "debug tools"
    description = "Installs the debug app on one device and seeds its database."
    dependsOn("assembleDebug")
    notCompatibleWithConfigurationCache("runs adb/python as an execution-time side effect")

    doLast {
        val applicationId = android.defaultConfig.applicationId
            ?: error("defaultConfig.applicationId is not set")
        val adb = resolveAdb()
        val serial = resolveDeviceSerial(adb)
        val dbPath = "/data/data/$applicationId/databases/prescription_database"
        val apk = project.file("build/outputs/apk/debug/app-debug.apk")
        if (!apk.exists()) error("Expected debug APK at $apk but it doesn't exist.")

        fun adbExec(vararg args: String, out: File? = null, ignoreFailure: Boolean = false): Pair<Int, String> =
            runProcess(listOf(adb.absolutePath, "-s", serial) + args.toList(), outFile = out, ignoreFailure = ignoreFailure)

        println("Seeding debug database on $serial ($applicationId)...")

        adbExec("install", "-r", apk.absolutePath)
        adbExec("shell", "am", "force-stop", applicationId)
        adbExec("shell", "am", "start", "-n", "$applicationId/.MainActivity")

        val deadline = System.currentTimeMillis() + 15_000
        var dbReady = false
        while (System.currentTimeMillis() < deadline) {
            val (exit, _) = adbExec("shell", "run-as", applicationId, "test", "-f", dbPath, ignoreFailure = true)
            if (exit == 0) {
                dbReady = true
                break
            }
            Thread.sleep(500)
        }
        if (!dbReady) {
            error(
                "Timed out waiting for $dbPath to be created on $serial. " +
                    "Is the debug app able to install and launch normally?"
            )
        }

        adbExec("shell", "am", "force-stop", applicationId)

        val tempDir = File.createTempFile("prescription-seed", "").let { placeholder ->
            placeholder.delete()
            placeholder.also { it.mkdirs() }
        }
        try {
            val tempDb = File(tempDir, "prescription_database")
            val tempWal = File(tempDir, "prescription_database-wal")
            adbExec("exec-out", "run-as", applicationId, "cat", dbPath, out = tempDb)
            
            adbExec(
                "exec-out", "run-as", applicationId, "cat", "$dbPath-wal",
                out = tempWal, ignoreFailure = true
            )
            if (tempWal.length() == 0L) tempWal.delete()

            runProcess(
                listOf(
                    "python3",
                    rootProject.file("tools/debug_seed/seed_database.py").absolutePath,
                    "--db", tempDb.absolutePath
                )
            )

            val stagedDb = "/data/local/tmp/prescription_database_seed"
            adbExec("push", tempDb.absolutePath, stagedDb)
            try {
                adbExec("shell", "run-as $applicationId sh -c 'cat $stagedDb > $dbPath'")
                adbExec("shell", "run-as", applicationId, "rm", "-f", "$dbPath-wal", "$dbPath-shm")
            } finally {
                adbExec("shell", "rm", "-f", stagedDb, ignoreFailure = true)
            }
        } finally {
            tempDir.deleteRecursively()
        }

        adbExec("shell", "am", "start", "-n", "$applicationId/.MainActivity")
        println("$applicationId now has seeded test data on $serial.")
    }
}