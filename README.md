# 𝖘𝖒𝖆𝖚𝖌 -0.1.0-

A simple DVR application.

> Smaug (/smaʊɡ/) is a dragon and the main antagonist in J. R. R. Tolkien's
> 1937 novel The Hobbit. He is a powerful and fearsome dragon who really cared
> about his posessions so he set up IP cameras to monitor his stuff.
## Installation
1. Install [leiningen](https://leiningen.org).
2. Install [ffmpeg, ffplay, and ffprobe](https://ffmpeg.org/download.html).
3. Clone this repository and navigate to the root directory.
4. `lein uberjar`

## Usage & Options
```bash
$ java -jar smaug-0.1.0-standalone.jar --help
usage: java -jar smaug-0.1.0-standalone.jar [options]

options:
  -c, --config PATH  /etc/smaug/config.json  Path to json config file.
  -d, --display                              Watch videos as they are recording
  -h, --help
```

## Configuration
```json
{
  "storage-directory": "/home/amaricich/videos/smaug/",
  "segments-minutes": 1,
  "max-segments": 2,
  "cameras": [
    {
      "name": "TestCamera",
      "url": "rtsp://user:pass@172.28.137.102:554/media/video1"
    }
  ]
}
```

 - `storage-directory`: The location recordings will be stored.
 - `segments-minutes`: The length in minutes of each recording.
   - eg: If set to 60, each recording file will be an hour long.
 - `max-segments`: The number of recording files that will be kept.
   - If `segments-minutes` is set to 60 and `max-segments` is set to 24,
     one days worth of recordings will be stored.
   - Once this threshold is met, the oldest recording for each stream will be
     removed.
 - `cameras`: A list of cameras or streams you want to record.
   - `name`: A unique identifier you want to use to identify the stream. This
     will be the name of the subdirectory your files will be stored.
     - If `name` is set to `AwesomeStream`, your video will be stored in
       `/home/amaricich/videos/smaug/AwesomeStream`.
   - `url`: The URL of the stream you want to record.

### Recordings
Recordings are stored in the following format:
```bash
[name][date-]-[time_].mp4

TestCamera2019-08-06-12_29_843.mp4
```

## License

Copyright © 2019 Alexander Maricich

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
