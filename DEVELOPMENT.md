# Development

## Requirements

A JDK 11 or later. The Maven wrapper (`./mvnw`, on Windows `mvnw.cmd`) downloads the Maven version that
`.mvn/wrapper/maven-wrapper.properties` names on its first run. The sources compile with `--release 11`, whichever
JDK builds them.

## Build

```bash
./mvnw package
```

runs the tests and writes `target/captchafox-java-<version>.jar`.

## Test

| Command | Runs |
|---|---|
| `./mvnw verify` | The tests against a local siteverify server, including every case of `src/test/resources/com/captchafox/__fixtures__/siteverify-cases.json`, and the check that the library has no dependencies at runtime. Needs no network once Maven and the dependencies are downloaded. CI runs it on Java 11, 17, 21 and 25 for every push to `main` and every pull request. |
| `./mvnw test -Plive` | The test against `https://api.captchafox.com/siteverify` with the public test keys. Needs the network. |
