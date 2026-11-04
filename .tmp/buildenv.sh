#!/usr/bin/env bash
# Build environment for this sandbox: the host denies writes to the system temp
# dir, so Gradle and the Kotlin/KSP compilers are pointed at a project-local
# scratch dir instead. Sourced by build/test invocations.
export JAVA_HOME="E:/Projects/Tag/.toolchain/jdk21/jdk-21.0.13+9"
export GRADLE_USER_HOME="E:/Projects/Tag/.toolchain/gradle-home"
export HOME="E:/Projects/Tag/.toolchain/home"
export USERPROFILE="E:/Projects/Tag/.toolchain/home"
export ANDROID_HOME="E:/Projects/Tag/.toolchain/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export TMP="E:/Projects/Tag/.tmp/ktmp"
export TEMP="E:/Projects/Tag/.tmp/ktmp"
export GRADLE_OPTS="-Djava.io.tmpdir=E:/Projects/Tag/.tmp/ktmp -Duser.home=E:/Projects/Tag/.toolchain/home"
export AAPT2_OVERRIDE="E:/Projects/Tag/.toolchain/android-sdk/build-tools/35.0.0/aapt2.exe"
export GRADLE_BIN="E:/Projects/Tag/.toolchain/gradle-8.9/gradle-8.9/bin/gradle"
