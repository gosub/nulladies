# shell.nix
{ pkgs ? import (fetchTarball "https://github.com/NixOS/nixpkgs/archive/nixos-25.05.tar.gz") {
    config = {
      android_sdk.accept_license = true;
      allowUnfree = true;
    };
  }
}:

let
  androidComposition = pkgs.androidenv.composeAndroidPackages {
    platformToolsVersion = "35.0.2";
    buildToolsVersions = [ "34.0.0" ];
    platformVersions = [ "34" ];
    includeEmulator = false;
    includeNDK = false;
    includeSources = false;
    includeSystemImages = false;
  };

  androidSdk = androidComposition.androidsdk;
in
pkgs.mkShell {
  buildInputs = [
    pkgs.jdk17
    pkgs.gradle
    androidSdk
  ];

  ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
  ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
  GRADLE_USER_HOME = ".gradle";

  shellHook = ''
    export PATH="${androidSdk}/libexec/android-sdk/build-tools/34.0.0:$PATH"
    export PATH="${androidSdk}/libexec/android-sdk/platform-tools:$PATH"
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    if [[ -t 1 ]]; then
      echo "Android dev environment ready." >&2
      echo "  javac:     $(javac -version 2>&1)" >&2
      echo "  gradle:    $(gradle --version 2>&1 | grep '^Gradle')" >&2
      echo "  adb:       $(which adb)" >&2
      echo "  ANDROID_HOME=$ANDROID_HOME" >&2
      echo "" >&2
      echo "Run 'make' to build, 'make run' to build+install+launch." >&2
    fi
  '';
}
