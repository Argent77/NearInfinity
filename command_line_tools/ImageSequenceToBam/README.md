# ImageSequenceToBam

Converts GIF frames into a compressed BAM v1 (`BAMC`) file.

## Usage

```bash
java -jar NearInfinity.jar --run-tool ImageSequenceToBam input.json
```

The input filename is passed to the tool. Paths in the JSON document are relative to the current working directory
unless they are absolute.

## Input format

```json
{
  "sources": [
    { "file": "images/walk-0.gif", "centerX": 16, "centerY": 32 },
    { "file": "images/walk-1.gif", "frame": 1 }
  ],
  "cycles": [
    [0, 1],
    [1, 0, 1]
  ],
  "output": "build/walk.bam"
}
```

Each `sources` entry creates one BAM frame from a GIF. `frame` selects the GIF frame and defaults to `0`.
`centerX` and `centerY` specify the frame center and default to `0`.

`cycles` contains zero-based indexes into `sources`. Each cycle must contain at least one frame index. The `output`
property specifies the destination BAM file; its parent directories are created automatically.

## Example

The [example input](examples/ImageSequenceToBam.json) converts two GIF files into `build/walk.bam`.
