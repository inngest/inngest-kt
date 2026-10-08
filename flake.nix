{
  description = "Inngest Kotlin SDK";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    inngest.url = "github:inngest/inngest.nix";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      inngest,
      ...
    }:
    flake-utils.lib.eachSystem (builtins.attrNames inngest.packages) (
      system:
      let
        pkgs = import nixpkgs { inherit system; };
      in
      {
        devShells.default = pkgs.mkShell {
          nativeBuildInputs = with pkgs; [
            inngest.packages.${system}.default

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
