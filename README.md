# Local Bridge Agent

Standalone Java utility for Phase 2 of the credential verification POC. It reads the
factory UID from a contactless card through a local PC/SC reader and exposes it to the
admin web app over localhost.

## Run

```powershell
mvn package
java -jar target/local-bridge-agent-0.0.1-SNAPSHOT.jar
```

The agent listens on `http://localhost:9000` by default.

You can override the port:

```powershell
java -jar target/local-bridge-agent-0.0.1-SNAPSHOT.jar --port=9100
```

## Endpoint

```http
GET http://localhost:9000/read-chip
```

Success:

```json
{ "chip_uid": "04AABBCCDD" }
```

Error:

```json
{ "error": "No card present. Place a chip on the reader and try again." }
```

The agent only reads the UID using the PC/SC `FF CA 00 00 00` command. It does not write
to the chip and does not call the backend or database.
