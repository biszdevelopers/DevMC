#!/usr/bin/env bash

set -euo pipefail

read -r -p "Plugin Name: " plugin_name
read -r -p "Plugin Owner: " plugin_owner
read -r -p "Plugin version: " plugin_version
read -r -p "Main Class: " main_class

# Tolerate CRLF input when the script is driven from Windows tooling.
plugin_name="${plugin_name%$'\r'}"
plugin_owner="${plugin_owner%$'\r'}"
plugin_version="${plugin_version%$'\r'}"
main_class="${main_class%$'\r'}"

if [[ ! "$plugin_name" =~ ^[A-Za-z][A-Za-z0-9_-]*$ ]]; then
  echo "Plugin Name must start with a letter and contain only letters, numbers, _ or -." >&2
  exit 1
fi

if [[ -z "$plugin_owner" || "$plugin_owner" == *['"'\\]* ]]; then
  echo "Plugin Owner cannot be empty or contain quotes or backslashes." >&2
  exit 1
fi

if [[ ! "$plugin_version" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
  echo "Plugin version contains unsupported characters." >&2
  exit 1
fi

if [[ ! "$main_class" =~ ^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+$ ]]; then
  echo "Main Class must be a fully qualified Java class name." >&2
  exit 1
fi

slug="$(printf '%s' "$plugin_name" | tr '[:upper:]_' '[:lower:]-')"
slug="${slug#plugin-}"
artifact_id="plugin-${slug}"
target_parent="${1:-.}"
output_dir="${target_parent%/}/${artifact_id}"
class_name="${main_class##*.}"
package_name="${main_class%.*}"
package_path="${package_name//./\/}"

if [[ -e "$output_dir" ]]; then
  echo "Refusing to overwrite existing path: $output_dir" >&2
  exit 1
fi

mkdir -p "$output_dir/src/main/java/$package_path"
mkdir -p "$output_dir/src/main/resources"

cat > "$output_dir/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>dev.bisz</groupId>
  <artifactId>$artifact_id</artifactId>
  <version>$plugin_version</version>
  <name>$plugin_name</name>
  <description>Stub Paper plugin.</description>

  <properties>
    <maven.compiler.release>25</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <repositories>
    <repository>
      <id>papermc</id>
      <url>https://repo.papermc.io/repository/maven-public/</url>
    </repository>
  </repositories>

  <dependencies>
    <dependency>
      <groupId>dev.bisz</groupId>
      <artifactId>plugin-bundler</artifactId>
      <version>2.0.0-SNAPSHOT</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>io.papermc.paper</groupId>
      <artifactId>paper-api</artifactId>
      <version>26.2.build.124-stable</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
        <configuration>
          <compilerArgs>
            <arg>-Xlint:all</arg>
          </compilerArgs>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-enforcer-plugin</artifactId>
        <version>3.5.0</version>
        <executions>
          <execution>
            <goals><goal>enforce</goal></goals>
            <configuration>
              <rules><requireJavaVersion><version>[25,)</version></requireJavaVersion></rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
EOF

cat > "$output_dir/src/main/resources/plugin.yml" <<EOF
name: "$plugin_name"
version: "$plugin_version"
main: $main_class
api-version: '26.2'
authors: ["$plugin_owner"]
depend: [Bundler]
EOF

cat > "$output_dir/src/main/java/$package_path/$class_name.java" <<EOF
package $package_name;

import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle entry point for the $plugin_name plugin. */
public final class $class_name extends JavaPlugin {
}
EOF

cat > "$output_dir/.gitignore" <<'EOF'
/target/
EOF

cat > "$output_dir/README.md" <<EOF
# $plugin_name

Minimal Paper plugin stub. It currently provides no features.

## Requirements

- Java 25 or newer
- Paper API 26.2
- Bundler 2.0.0-SNAPSHOT

## Build

\`\`\`shell
mvn package
\`\`\`
EOF

echo "Created $plugin_name at $output_dir"
