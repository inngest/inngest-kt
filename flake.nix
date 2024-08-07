{
  description = "Inngest Kotlin SDK";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let pkgs = import nixpkgs { inherit system; };

      in {
        devShells.default = pkgs.mkShell {
          nativeBuildInputs = with pkgs; [
            kotlin
            gradle

            # Oldest java version support
            jdk8

            # Tooling
            detekt
            ktfmt
            ktlint

            # For integration test
            nodejs

            # LSP
            kotlin-language-server
          ];
        };
      });
}
