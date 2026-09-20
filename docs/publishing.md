# Publishing to JetBrains Marketplace

## Release checklist

1. Update `version` and `changeNotes` in `build.gradle.kts`.
2. Run the checks:

   ```bash
   python3 tools/check.py
   ./gradlew clean test
   ./gradlew verifyPluginProjectConfiguration verifyPluginStructure
   ```

3. Build the distribution:

   ```bash
   ./gradlew buildPlugin
   ```

   The ZIP is written to `build/distributions/tunnelvision-<version>.zip`.

4. Install that ZIP in a clean IDE once and test focus, dynamic mode, word mode, context areas,
   color settings, navigation, and editor teardown.

5. Optionally run compatibility verification. It downloads several IDEs:

   ```bash
   ./gradlew verifyPlugin
   ```

6. Store a Marketplace token outside the repository, then publish:

   ```properties
   # ~/.gradle/gradle.properties
   publishToken=perm:...
   ```

   ```bash
   ./gradlew publishPlugin
   ```

The plugin is MIT licensed. An icon is optional; until one is added, the Marketplace displays its
standard placeholder.
