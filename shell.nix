let pkgs = import <nixos-23.11> { };

in pkgs.mkShell {
  nativeBuildInputs = [
    # Kotlin
    pkgs.kotlin
    pkgs.gradle

    # Oldest java version support
    pkgs.jdk8

    # Tooling
    pkgs.detekt
    pkgs.ktfmt
    pkgs.ktlint

    # LSPs
    pkgs.kotlin-language-server
  ];
}
