# Publishing the plugin to the JetBrains Marketplace

A short walkthrough, using the tasks this build already provides. Everything runs from the
repository root, and nothing here needs a JetBrains IDE installation.

## What the build gives you

| Task | What it is for |
| --- | --- |
| `./gradlew buildPlugin` | writes `build/distributions/tunnelvision-<version>.zip`, the artifact you upload |
| `./gradlew verifyPluginProjectConfiguration` | checks the descriptor and the Gradle configuration for a listing |
| `./gradlew verifyPluginStructure` | checks the zip structure and `plugin.xml` completeness |
| `./gradlew verifyPlugin` | runs the Plugin Verifier against the recommended IDE builds (downloads GBs) |
| `./gradlew signPlugin` / `verifyPluginSignature` | signs the zip so users do not see an "unsigned plugin" warning |
| `./gradlew publishPlugin` | uploads the zip to the Marketplace |

The plugin id, name, version, description, vendor and the IDEA version range come from
`pluginConfiguration` in `build.gradle.kts`; `src/main/resources/META-INF/plugin.xml` only holds
the wiring, so there is a single source of truth.

## 1. Fill in what a listing needs

- **Licence.** Add a `LICENSE` file (for example Apache-2.0) and mention it in the plugin
  description. If you bundle third-party code, add a `NOTICE` too.
- **Icon.** `src/main/resources/META-INF/pluginIcon.svg` (and `pluginIcon_dark.svg`), a 40×40
  viewBox. Without it the Marketplace shows a grey placeholder.
- **Change notes**, which the IDE shows when the user updates:

  ```kotlin
  pluginConfiguration {
      changeNotes = """
          <ul>
            <li>First release: PSI and word sources, four highlight areas, settings and colors.</li>
          </ul>
      """.trimIndent()
  }
  ```

- Then let the build tell you what is still missing: `./gradlew verifyPluginProjectConfiguration`.

## 2. Decide the compatibility envelope

`pluginConfiguration { ideaVersion { sinceBuild = "252"; untilBuild = provider { null } } }`.

`sinceBuild = 252` means IntelliJ IDEA 2025.2 and newer. Leave `untilBuild` open unless you have a
reason to cap it: the Marketplace then offers the plugin to newer IDEs, and the verifier warns you
about breakage instead of users hitting it.

Worth stating on the listing page: the JavaScript/TypeScript integration needs the JavaScript
plugin, which ships with Ultimate only. Community installs still get Java, Kotlin and `word` mode.

## 3. Prove it before uploading

```bash
./gradlew test                 # 103 tests
./gradlew verifyPlugin         # binary compatibility with the recommended IDE builds
./gradlew buildPlugin          # the zip you will upload
```

Then install that zip by hand into a clean IDE once (`Settings → Plugins → ⚙ → Install Plugin from
Disk…`) and walk the feature list: focus, dynamic mode, word source, the four areas, the colour
page, the navigation keys, and teardown when you close the tab.

Note: `verifyPlugin` downloads and unpacks each IDE it checks, which adds up to several GB. It is
worth running on purpose rather than on every build, and `./gradlew cleanSandbox clean` plus
removing the unpacked copies under `~/.gradle/caches/*/transforms` reclaims it afterwards.

## 4. Get a token and publish

1. Sign in at <https://plugins.jetbrains.com>, then **Profile → My Tokens → Generate** to create a
   permanent token.
2. Wire the token into the build rather than typing it into the shell history:

   ```kotlin
   intellijPlatform {
       publishing {
           token = providers.environmentVariable("PUBLISH_TOKEN")
           // channels = listOf("default")   // "eap" publishes a preview users can opt into
       }
   }
   ```

3. Publish:

   ```bash
   PUBLISH_TOKEN=<your token> ./gradlew publishPlugin
   ```

   The first upload of a new plugin goes to manual review; updates to an existing plugin are
   published immediately. You can also upload the zip by hand on the Marketplace site
   (**Plugins → Upload plugin**) if you would rather not hand the build a token.

## 5. Signing (optional, free)

```bash
./gradlew signPlugin \
  -Psigning.certificateChain=... -Psigning.privateKey=... -Psigning.password=...
./gradlew verifyPluginSignature
```

The properties map onto `intellijPlatform { signing { certificateChain = ...; privateKey = ...;
password = ... } }`. Keep the key material in CI secrets or a local file that is git-ignored, never
in the repository.

## 6. Updates

Bump `version` in `build.gradle.kts`, add change notes, and publish again: the same plugin id
updates the existing listing, and installed users get it through the IDE's update check. Raising
`sinceBuild` drops users on older IDEs, so do it deliberately and say so in the change notes.

## 7. Release from your own machine

The heavy part of the toolchain stays local: the IDE the build compiles against is downloaded and
unpacked on a cold machine (about 6 GB), and `verifyPlugin` wants roughly 22 GB more for the
recommended IDE set, while a standard `ubuntu-latest` runner has about 14 GB free. On a private
repository that job would also spend 10-25 minutes of your monthly minutes per run. A machine that
already has the caches warm does all of it faster and for free.

What does run in CI (`.github/workflows/checks.yml`) is a static check that needs no toolchain and
downloads nothing, so it takes seconds and costs nothing:

```bash
python3 tools/check.py
```

It parses the descriptors and the colour fragments, resolves every class a descriptor points at,
checks that each colour key is one the plugin declares and that the light and dark fragments style
the same keys, and refuses tracked build output or a Marketplace token in the tree. Run the same
command before a release; see `tools/check.py` for what each check is for.

The release checklist, from the repository root:

```bash
# once: keep the token outside the repository, where it cannot be committed
echo "publishToken=perm:your-token-here" >> ~/.gradle/gradle.properties

# every release
./gradlew clean test                                   # 103 tests
./gradlew verifyPluginProjectConfiguration verifyPluginStructure
./gradlew buildPlugin                                  # the zip to inspect
#   install build/distributions/tunnelvision-<version>.zip into a clean IDE once and click through
./gradlew verifyPlugin                                 # optional: ~10 min and ~22 GB of IDEs
./gradlew signPlugin verifyPluginSignature             # optional: only if you sign releases
./gradlew publishPlugin                                # uploads to the Marketplace
```

Reading the token from `~/.gradle/gradle.properties` instead of the shell keeps it out of your shell
history and out of the repository:

```kotlin
intellijPlatform {
    publishing {
        // ~/.gradle/gradle.properties, never the project's
        token = providers.gradleProperty("publishToken")
    }
}
```

For a one-off run, wiring `token` to `providers.environmentVariable("PUBLISH_TOKEN")` and calling
`PUBLISH_TOKEN=<token> ./gradlew publishPlugin` works just as well.

Once you have verified a release, reclaim the space it used before the next one - and remember that
Windows still needs the WSL disk compacted before it shows up there:

```bash
./gradlew cleanSandbox clean
# drop the unpacked IDE copies the verifier left behind
find ~/.gradle/caches/*/transforms -maxdepth 3 -type d -name 'idea*' -path '*/transformed/*' \
  | sed 's#/transformed/.*##' | sort -u | xargs -r rm -rf
```
