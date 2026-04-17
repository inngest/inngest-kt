{
  description = "Inngest Kotlin SDK";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      ...
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs { inherit system; };

        inngestRelease = {
          version = "1.17.9";
          x86_64-darwin = {
            os = "darwin";
            arch = "amd64";
            hash = "sha256-9gbox1f9nABWf/KP7QIJDBqWk6VR+sosJuY7HmwHC9g=";
          };
          aarch64-darwin = {
            os = "darwin";
            arch = "arm64";
            hash = "sha256-uk4e0/gOVEJFAr1Z2eVzCf8abR8IPHbYadQKmH5Y97w=";
          };
          x86_64-linux = {
            os = "linux";
            arch = "amd64";
            hash = "sha256-Vh9uCOBNzdKfZngrhupWjntWMJvNBzYg/sGPGaBoOeI=";
          };
          aarch64-linux = {
            os = "linux";
            arch = "arm64";
            hash = "sha256-iM6sackbdmz6Sbge2Xjx8kNlZ6uyUToE2NiojlSn82s=";
          };
        };

        inngestAsset =
          inngestRelease.${system} or (throw "Unsupported system for inngest CLI release: ${system}");

        inngest = pkgs.stdenvNoCC.mkDerivation {
          pname = "inngest";
          inherit (inngestRelease) version;

          src = pkgs.fetchurl {
            url =
              "https://github.com/inngest/inngest/releases/download/v${inngestRelease.version}/"
              + "inngest_${inngestRelease.version}_${inngestAsset.os}_${inngestAsset.arch}.tar.gz";
            inherit (inngestAsset) hash;
          };

          nativeBuildInputs = with pkgs; [
            gnutar
            gzip
          ];

          sourceRoot = ".";
          dontConfigure = true;
          dontBuild = true;

          installPhase = ''
            runHook preInstall
            install -Dm755 inngest $out/bin/inngest
            install -Dm644 LICENSE.md $out/share/licenses/inngest/LICENSE.md
            runHook postInstall
          '';
        };
      in
      {
        devShells.default = pkgs.mkShell {
          nativeBuildInputs = with pkgs; [
            inngest

            kotlin
            gradle

            # Oldest java version support
            jdk8

            # Tooling
            detekt
            ktfmt
            ktlint
            git-cliff

            # LSP
            kotlin-language-server
          ];

          shellHook = ''
            if [ -z "''${JAVA_HOME:-}" ]; then
              export JAVA_HOME="${pkgs.jdk8}"
              export PATH="$JAVA_HOME/bin:$PATH"
            fi
          '';
        };
      }
    );
}
