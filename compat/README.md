# Compatibility fixtures

`consumer/` is a standalone Gradle build (not part of the root build, no `includeBuild`) that applies
the **published** Store Studio plugin and only has to compile (it declares no screenshots), so it catches dependency-metadata problems the samples can't.
It is not run by CI. Check it by hand against the oldest and newest supported AGP, Kotlin, KSP and Gradle.

    ./gradlew publishToMavenLocal --no-configuration-cache
    gradle -p compat/consumer -Pcompat.agp=9.1.1 -Pcompat.kotlin=2.4.10 buildEnvironment :app:compileDebugKotlin

Use the Gradle version under test (a downloaded distribution or `gradle-9.x/bin/gradle`); versions default to the latest tested.
