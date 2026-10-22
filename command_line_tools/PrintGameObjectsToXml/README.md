# PrintGameObjectsToXml

Loads resources from an Infinity Engine game and writes their parsed fields to one XML file per resource.

## Usage

```bash
java -jar NearInfinity.jar --run-tool PrintGameObjectsToXml input.json
```

The input filename is passed to the tool. Relative paths in the JSON document are resolved relative to the directory
containing the input JSON file.

## Input format

```json
{
  "game": "C:/Games/BaldursGate2/chitin.key",
  "weidu": "C:/Games/BaldursGate2/weidu.exe",
  "resources": [
    "^sw.*\\.itm$",
    "^spwi.*\\.spl$"
  ],
  "output": "build/game-objects"
}
```

`game` must point to the game's `chitin.key` file. Near Infinity loads the game resources, override files, and dialog
table while processing the request.

`weidu` must point to a WeiDU executable. The path is used to initialize Near Infinity's WeiDU option before the
command-line tool loads GUI-backed resource definitions. This also avoids depending on the user's saved Near Infinity
preferences. Relative paths are resolved relative to the input JSON file.

`resourceRegexp` is an optional array of regular expressions. Expressions are matched against complete resource
filenames, including their extensions, without regard to case. Resources in the override folder and resources in the
game's BIFF archives are both considered. If an override resource has the same name as an archived resource, the
override version is used.

`looseFiles` is an optional array of resource file paths. Paths may be absolute or relative to the input JSON file.
Each file must exist and is opened directly as an external resource; it does not need to be inside the selected game
directory.

At least one of `resourceRegexp` and `looseFiles` must contain an entry. If both arrays are non-empty, both game
resources matching the regular expressions and all listed loose files are exported.

`output` specifies the output folder. One XML file is created for each matching resource, using the resource filename
plus `.xml`; for example, `sw1h01.itm` is written to `sw1h01.itm.xml`. If the path exists, it must be a folder.

`existingFolder` controls how an existing output folder is handled:

- `failIfNotEmpty`: requires the folder to be empty.
- `failOnOverwrite`: allows existing files, but scans all output filenames and fails before writing if any would be overwritten.
- `overwrite`: writes output files over existing files.
- `clear`: deletes the folder contents before writing.
- `create_overwrite`: creates a missing folder and overwrites existing output files.
- `craete_clear`: creates a missing folder and clears existing folder contents before writing.

The default is `create_overwrite`. All other modes fail if the output folder does not exist. All modes fail when
`output` exists but is not a folder.

Each matching resource is written as a pretty-printed XML document. Fields are represented using the parsed Near Infinity
structure and field names used by the resource viewer. Elements are sorted by file offset and include `offset` and `size`
attributes. Numeric values, lookup-table values, and dialog.tlk references include a hexadecimal `value` attribute;
their displayed description is the element text. Resource references use filenames including their extensions.
Bitmasks include the hexadecimal `value` attribute and nested elements for each set bit. Repeated fields are emitted
with the same element name and an `id` attribute; numeric suffixes in generated array field names are removed.

## Example

The [example input](examples/PrintGameObjectsToXml.json) extracts item and spell resources. Run
[print-game-objects.bat](examples/print-game-objects.bat) from this directory after changing the game path in the
sample JSON file and setting its WeiDU executable path.
