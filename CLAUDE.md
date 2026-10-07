# Arcforge

- Textures: follow docs/TEXTURE_STYLE.md (Distillation Array style: flat tones, 1px bevels,
  top-left light, machine steel ramp). Never add gradients, dithering or speckle.
- Releasing: follow docs/RELEASING.md. A pushed v<mod_version>+<minecraft_version> tag (e.g. v2.6.0+26.3) runs
  .github/workflows/release.yml, which publishes the mod and API jars on GitHub and uploads the mod jar to CurseForge.
  Tags up to v2.5.0 predate the scheme.
- Minecraft versions: one branch each; main is the newest, mc/<major.minor> the others (see "Minecraft versions" in
  docs/RELEASING.md). The same version number has the same changes on every branch: make a change on one branch and
  cherry-pick it to the others. Keep version-specific code small and in one place.
