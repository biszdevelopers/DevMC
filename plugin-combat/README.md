# combat

Minimal Spigot plugin stub. It currently provides no features.

## Requirements

- Java 17 or newer
- Spigot API 1.20.1
- Bundler 2.0.0-SNAPSHOT

## Build

`shell
mvn package
`

Before building a generated plugin, install the local shared dependencies from
the server directory:

`shell
mvn -f ../plugin-bundler/pom.xml install
mvn -f ../plugin-currency/pom.xml install
`

Copy the resulting JAR from 	arget/ to the server's plugins/ directory.
