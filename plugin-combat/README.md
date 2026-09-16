# combat

Paper/Youer combat plugin.

## Requirements

- Java 25 or newer
- Paper API 26.2
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
