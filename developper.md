# What is Gradle? (a web-dev's guide)

**Gradle is the build tool** — the thing that turns this project's source code into
the shippable artifact (the `.jar`). In web terms it's your
**`package.json` + npm/yarn + webpack, rolled into one**.

It does three jobs.

## 1. Dependency management (like `npm install`)

You declare what your code needs — Minecraft, Fabric Loader, Fabric API, the `common`
module — and Gradle downloads them from repositories (Maven repos, the JVM equivalent
of the npm registry) and puts them on the compile path. That's what these lines in
`build.gradle` are:

```groovy
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation project(':common')   // depend on a local module, like a workspace package
}
```

## 2. Building (like `webpack` / `tsc` / your bundler)

It compiles the `.java` files into `.class` bytecode, processes resources
(`fabric.mod.json`), and packages everything into a `.jar`. For us there's an extra
step: the **fabric-loom** plugin (a Gradle plugin specific to Minecraft modding) also
"remaps" the jar — rewrites it to use Minecraft's obfuscated internal names so it
actually runs against the real game. `remapJar` is that task.

## 3. Task running (like npm scripts)

Everything is a **task**. `./gradlew :server:build` means "run the `build` task in the
`server` module." `:common:build`, `:server:remapJar` — same idea as
`npm run <script>`, with `:module:task` addressing the monorepo module.

## Mapping to things in this repo

| Gradle thing | Web analogy |
|---|---|
| `build.gradle` | `package.json` + build config |
| `settings.gradle` | the monorepo/workspaces declaration (lists the 3 modules) |
| `gradle.properties` | a shared `.env` / config of version variables |
| `./gradlew` | a pinned local toolchain runner — like committing your exact npm version so everyone builds identically |
| `:common`, `:client`, `:server` | workspace packages in a monorepo |
| Maven Central / Fabric maven | the npm registry |

## Two specifics worth knowing

- **`./gradlew` (the wrapper)** — a script + a pinned version number committed in the
  repo, so anyone who clones it gets the *exact* Gradle version (8.12 here) without
  installing Gradle globally. Same philosophy as committing a lockfile.
- **It runs on the JVM and needs a JDK** — Gradle is a Java program building Java
  code, so it needs Java itself (JDK 21 for us). That's the
  `org.gradle.java.installations.paths` line in `gradle.properties`. The web
  equivalent: "this build only works on Node 20."

## One real difference from the npm world

Gradle build files are **actual code** (a Groovy/Kotlin script), not static JSON. So
`build.gradle` has loops, variables, and logic — that's why both `build.gradle`
(configuring the subprojects) and the mod's command-building code look like
programs: because they are.
