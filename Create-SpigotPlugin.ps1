[CmdletBinding()]
param (
    [Parameter(Position = 0)]
    [string]$TargetParent = "."
)

$ErrorActionPreference = "Stop"

$plugin_name = Read-Host "Plugin Name"
$plugin_owner = Read-Host "Plugin Owner"
$plugin_version = Read-Host "Plugin version"
$main_class = Read-Host "Main Class"

# Trim whitespace and potential CRLF artifacts
$plugin_name = $plugin_name.Trim()
$plugin_owner = $plugin_owner.Trim()
$plugin_version = $plugin_version.Trim()
$main_class = $main_class.Trim()

if ($plugin_name -cnotmatch "^[A-Za-z][A-Za-z0-9_-]*$") {
    [Console]::Error.WriteLine("Plugin Name must start with a letter and contain only letters, numbers, _ or -.")
    exit 1
}

if ([string]::IsNullOrEmpty($plugin_owner) -or $plugin_owner -match '[\''"\\]') {
    [Console]::Error.WriteLine("Plugin Owner cannot be empty or contain quotes or backslashes.")
    exit 1
}

if ($plugin_version -cnotmatch "^[A-Za-z0-9][A-Za-z0-9._-]*$") {
    [Console]::Error.WriteLine("Plugin version contains unsupported characters.")
    exit 1
}

if ($main_class -cnotmatch "^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+$") {
    [Console]::Error.WriteLine("Main Class must be a fully qualified Java class name.")
    exit 1
}

# Slug generation: lowercase, replace _ with -, remove leading plugin-
$slug = $plugin_name.ToLower().Replace('_', '-') -replace '^plugin-', ''
$artifact_id = "plugin-$slug"

# Resolve output directory
$TargetParent = $TargetParent.TrimEnd('\', '/')
$output_dir = Join-Path $TargetParent $artifact_id

# Parse class and package names
$lastDotIndex = $main_class.LastIndexOf('.')
$class_name = $main_class.Substring($lastDotIndex + 1)
$package_name = $main_class.Substring(0, $lastDotIndex)
$package_path = $package_name.Replace('.', '/')

if (Test-Path $output_dir) {
    [Console]::Error.WriteLine("Refusing to overwrite existing path: $output_dir")
    exit 1
}

# Create directories
$java_dir = Join-Path $output_dir "src/main/java/$package_path"
$resources_dir = Join-Path $output_dir "src/main/resources"

New-Item -ItemType Directory -Force -Path $java_dir | Out-Null
New-Item -ItemType Directory -Force -Path $resources_dir | Out-Null

# pom.xml
$pomContent = @"
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
    <!-- Currency exposes CurrencyManager to plugins that use the shared purse API. -->
    <dependency>
      <groupId>dev.bisz</groupId>
      <artifactId>plugin-currency</artifactId>
      <version>1.0.0-SNAPSHOT</version>
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
"@
Set-Content -Path (Join-Path $output_dir "pom.xml") -Value $pomContent -Encoding UTF8

# plugin.yml
$pluginYmlContent = @"
name: "$plugin_name"
version: "$plugin_version"
main: $main_class
api-version: '26.2'
authors: ["$plugin_owner"]
depend: [Bundler, Currency]
"@
Set-Content -Path (Join-Path $resources_dir "plugin.yml") -Value $pluginYmlContent -Encoding UTF8

# Main Class file
$javaContent = @"
package $package_name;

import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle entry point for the $plugin_name plugin. */
public final class $class_name extends JavaPlugin {
}
"@
Set-Content -Path (Join-Path $java_dir "$class_name.java") -Value $javaContent -Encoding UTF8

# .gitignore
# Using single quotes to mimic Bash's EOF behavior (no variable interpolation)
$gitIgnoreContent = @'
/target/
'@
Set-Content -Path (Join-Path $output_dir ".gitignore") -Value $gitIgnoreContent -Encoding UTF8

# README.md
$readmeContent = @"
# $plugin_name

Minimal Paper plugin stub. It currently provides no features.

## Requirements

- Java 25 or newer
- Paper API 26.2
- Bundler 2.0.0-SNAPSHOT

## Build

```shell
mvn package
```

Before building a generated plugin, install the local shared dependencies from
the server directory:

```shell
mvn -f ../plugin-bundler/pom.xml install
mvn -f ../plugin-currency/pom.xml install
```

Copy the resulting JAR from `target/` to the server's `plugins/` directory.
"@
Set-Content -Path (Join-Path $output_dir "README.md") -Value $readmeContent -Encoding UTF8

Write-Host "Created $plugin_name at $output_dir"
