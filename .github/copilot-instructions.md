# Near Infinity Copilot Instructions

## Build and validation

Run commands from the `NearInfinity\` directory, where `build.xml` and the `lib\` directory are located. The project requires Apache Ant and a JDK; production compilation targets Java 8 (`source`, `target`, and `release` are all set to 8), even though a newer JDK may be used to run Ant.

```bash
# Clean, compile all sources, copy non-Java resources, and create NearInfinity.jar
ant -noinput

# Explicit equivalent of the default target
ant -noinput -buildfile build.xml compile

# Remove generated build output and the jar
ant clean
```

There is no configured unit-test framework, test source tree, or lint target. Treat a successful Ant compile as the standard automated validation. For a manual smoke test after building:

```bash
java -jar NearInfinity.jar
```

Parser sources are a special case. `src\org\infinity\resource\bcs\parser\BafParser.jjt` is the source grammar; regenerate the JavaCC/JJTree parser with:

```bash
ant parser-clean
ant parser-generate
ant
```

The CI build uses JDK 8 and `ant -noinput -buildfile build.xml`. Release packaging is handled by GitHub Actions and external installer assets, not by a local Ant target.


## Architecture

Near Infinity is a Swing desktop browser/editor for BioWare Infinity Engine game data. `org.infinity.NearInfinity` owns application startup, the main window, preferences, menus, game selection, and the top-level resource browser.

The central data flow is:

1. `Profile` identifies the selected game/engine and exposes game-specific paths, features, formats, and rules.
2. `ResourceFactory` opens the selected game's `chitin.key`, BIF archives, override folders, and loose files, then maps each `ResourceEntry` to a format-specific `Resource` implementation.
3. `ResourceTreeModel` and `org.infinity.gui.ResourceTree` present those entries and route selection to viewers/editors.
4. Format packages under `org.infinity.resource` parse binary/text formats into `StructEntry`/`AbstractStruct` hierarchies. Their `Viewer` classes and related GUI classes render or edit those structures.
5. Shared `datatype`, `util`, `key`, `effects`, `check`, and GUI packages provide binary field decoding, caching, platform/file access, validation, resource indexing, and reusable Swing components.

Most supported formats have a dedicated package below `src\org\infinity\resource` (for example `are`, `cre`, `dlg`, `itm`, `spl`, `sto`, `bcs`, and `graphics`). Format dispatch belongs in `ResourceFactory`; game-dependent behavior belongs in `Profile`, rather than being duplicated in individual viewers. BCS parsing/compilation is implemented by `resource\bcs` and uses the generated JavaCC parser under `resource\bcs\parser`.

The Ant build compiles `src\` into `build\src`, copies every non-Java source resource into that output, and packages the result plus bundled third-party jars into the executable `NearInfinity.jar`. Dependencies are intentionally checked into `lib\` and referenced directly by `build.xml`.

## Repository conventions

- Match the existing Java style: two spaces for indentation, no wildcard imports, UTF-8 source, one final newline, no trailing whitespace, and generally keep lines below 120 columns.
- Preserve Java 8 compatibility. Do not introduce APIs or language features that require a newer runtime without an explicit compatibility decision; `src\nearinfinity.properties` records the minimum runtime as `8`.
- Keep user-facing and format-specific resources beside the relevant package/resource implementation. Non-Java files under `src\` are runtime inputs and are copied into the jar by Ant.
- Use the existing resource abstractions (`Resource`, `ResourceEntry`, `AbstractStruct`, `StructEntry`, `Viewable`) and factories/caches instead of creating parallel parsing or file-access paths.
- Respect the distinction between physical loose files and entries inside BIFF archives. Use the existing `FileResourceEntry`, `BIFFResourceEntry`, `Keyfile`, `FileManager`, and `ResourceFactory` behavior when reading or exporting resources.
- Keep game/engine conditionals centralized around `Profile` and its enums/properties. Resource implementations should query the active profile rather than infer game behavior from filenames or ad-hoc flags.
- Generated parser output is derived from `BafParser.jjt`. Change the grammar and regenerate with the Ant parser targets instead of hand-editing generated parser classes.
- Third-party jars and their license/source archives are part of the repository's build input; update `build.xml` and the corresponding checked-in library/license files together when changing a dependency.
- Follow the documented branch workflow: normal feature work is based on `devel`; `master` tracks stable version changes. Keep changes focused and validate them with the Ant build before merging.
