# Command-line tools

Near Infinity exposes tools through the `--run-tool` option:

```bash
java -jar NearInfinity.jar --run-tool <tool-class> <file>
```

The tool class name is resolved in the `org.infinity.cli` package. Run the command from the directory whose relative
paths should be used by the tool.

## Available tools

- [ImageSequenceToBam](ImageSequenceToBam/README.md)
- [PrintGameObjectsToXml](PrintGameObjectsToXml/README.md)
