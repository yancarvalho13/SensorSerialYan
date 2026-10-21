# SensorSerialYan

An open-source Java project for communicating with the **Fonkan FI-801 RFID reader**, parsing its binary responses, and exposing tag readings through a REST API.

## About

The project code was written entirely by hand. No AI slop.

No reverse engineering was performed. Development was based on analyzing data inputs and outputs and parsing the buffers exchanged with the reader.

The goal is straightforward: send commands to the device, read its responses, and turn raw bytes into usable tag IDs.

## What it does

- Communicates with the reader over TCP sockets.
- Builds binary command packets and outgoing checksums.
- Parses response headers, command echoes, and payloads.
- Reads EPC tag IDs, including additional pages of results.
- Collects distinct tag IDs and converts them to hexadecimal strings.
- Provides HTTP endpoints for listing tags and finding a specific tag.

`WirelessReader` is the driver used by the application. The repository also includes a deprecated `Reader` implementation with serial and network communication routines.

## Requirements

- Java 17.
- Maven, or the included Maven Wrapper.
- A Fonkan FI-801 reader reachable over the network.
- RFID tags compatible with the reader's configuration.

The application uses Spring Boot 3.5.5. The legacy serial implementation uses jSerialComm.

## Running

Clone the repository:

```sh
git clone https://github.com/yancarvalho13/SensorSerialYan.git
cd SensorSerialYan
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

On Linux or macOS:

```sh
sh mvnw spring-boot:run
```

The HTTP API uses port `8080` by default. The active Spring profile defaults to `prod` and can be changed through the `APP_PROFILE` environment variable.

The current application profiles disable JDBC and JPA auto-configuration, so a database is not required for the tag-reading flow. A PostgreSQL Docker Compose file is included separately.

## Reader connection

The list endpoint currently connects to `192.168.2.1:6000`, configured directly in [`SensorController.java`](src/main/java/org/Yan/infra/controller/SensorController.java). Update that address and port to match your reader before using it.

The lookup endpoint accepts the reader IP address and port in the request path. In both cases, the machine running the application must be able to reach the device over TCP.

## API

| Method | Endpoint | Description |
| --- | --- | --- |
| GET | `/sensor` | Scan the configured reader and return distinct tags. |
| GET | `/sensor/{ip}/{porta}/{tagId}` | Scan the specified reader and find a tag by its EPC ID. |

Examples:

```sh
curl http://localhost:8080/sensor
curl http://localhost:8080/sensor/192.168.2.1/6000/E280691500004021E743F052
```

These requests perform a hardware scan rather than querying stored readings. A normal scan runs for approximately eight seconds; communication delays can make requests take longer. Tag lookup is case-insensitive.

## Project structure

```text
src/main/java/org/Yan/
  driver/             Reader communication and binary buffer parsing
  service/            Tag scanning and lookup
  infra/controller/   REST endpoints
  infra/DTO/          API data objects
  infra/security/     HTTP request logging
  exceptions/         Device and API error handling
src/main/resources/   Spring application profiles
```

## Scope

This implementation targets the Fonkan FI-801. Compatibility with other readers or firmware versions is not guaranteed. Hardware communication requires a real, correctly configured device; starting the API alone does not produce tag readings.

## License

Licensed under the **GNU General Public License v3.0 only** (`GPL-3.0-only`).

You may use, study, modify, and redistribute this project. If you distribute copies or derivative versions, you must keep them under the GPL-3.0 and provide the corresponding source code as required by the license. You cannot distribute a closed-source derivative of this project.

Private use and private modifications do not require publication. These obligations apply to this project's code and covered derivatives, not to unrelated software merely stored alongside it.

See [LICENSE](LICENSE) for the full terms.
