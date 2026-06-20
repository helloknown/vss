# IntelliJ Visual SourceSafe Integration

Visual SourceSafe (VSS) plugin for IntelliJ IDEA and compatible JetBrains IDEs.

This repository is a maintained fork of the [original JetBrains project](https://github.com/JetBrains/vss). The upstream project is no longer actively maintained; this fork keeps the plugin working on recent IDE versions and adds fixes and improvements.

The plugin provides IntelliJ integration with [Microsoft Visual SourceSafe](http://msdn.microsoft.com/en-us/library/3h0544kx(v=vs.80).aspx).

Visual SourceSafe is a file-level version control system. This plugin allows using it from within the product, making even refactoring consequences transparent for the user.

## Features

* Dedicated page under the Version Control node in Settings/Preferences
* Frequently used commands: Open Source Safe Explorer, Check In/Out, Add, Undo Checkout, Get Latest Version
* Next, Previous, Rollback, and Old text actions from the gutter bar in changed locations
* Checkout occupancy indicators and status bar integration

## Requirements

* JDK 17+
* IntelliJ IDEA 2024.2 or later (Community or Ultimate)

## Build and run

1. Clone this repository and open it in IntelliJ IDEA.
2. Ensure the Gradle wrapper is used (the project uses the IntelliJ Platform Gradle Plugin).
3. Run the **Run Plugin** Gradle task, or use the **Plugin** run configuration if present.
4. To build a distributable plugin JAR:

   ```bash
   ./gradlew buildPlugin
   ```

   On Windows:

   ```bash
   gradlew.bat buildPlugin
   ```

   The JAR is generated under `build/distributions/`.
5. Install the JAR via **Settings/Preferences → Plugins → Install Plugin from Disk**.

## Version

Current plugin version: **1.1.0** (see `gradle.properties`).

## License

See [LICENSE.txt](LICENSE.txt).
