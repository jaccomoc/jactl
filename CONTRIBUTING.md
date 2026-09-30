# Contributing to Jactl

Thanks for your interest in contributing to Jactl.
Bug reports, fixes, docs improvements, and new ideas are all welcome.

## Reporting bugs

- Search [existing issues](https://github.com/jaccomoc/jactl/issues) first to avoid duplicates.
- Include your Jactl version, your Java version, and a minimal Jactl script that reproduces the
  problem, along with what you expected to happen vs. what actually happened.

## Suggesting features

Open an issue to discuss it before writing code, especially for anything big.
It saves everyone from wasted effort if the feature doesn't fit the project's direction.

## Setting up a dev environment

### Requirements

- Java 8 or later
- Gradle 8.0.2 (the included `gradlew` wrapper will download this for you)

## Making a change

1. Fork the repo and create a branch from `main`.
2. Make your change, keeping it focused — one logical change per PR.
3. **Add or update tests.** Bug fixes should include a test that fails without the fix.
New features need tests covering the main behaviour and edge cases. Most tests live in
   `src/test/java/io/jactl/`:
   - `CompilerTest*.java` / `ScriptTest.java` - core language tests
   - `ClassTests.java` - class/inheritance tests
   - `SwitchTests.java` - pattern matching tests
   - `BuiltinFunctionTests*.java` - built-in function tests
   - `RegisterClassTests.java` - Java class registration tests
   - `engine/JactlScriptEngineTest.java` - JSR 223 tests

   `BaseTest` provides the test infrastructure used by most test classes.
4. Make sure the full test suite passes locally:
   ```shell
   ./gradlew build testAll
   ```
5. Update docs if behaviour changes (see the [docs](docs) directory, published at
   [jactl.io/docs](https://jactl.io/docs)).
6. Open a PR against `jaccomoc/jactl`'s `main` branch describing what changed and why, and link
   any related issue (e.g. `Fixes #123`).

## Code style

Follow the style of the surrounding code in whichever file you're editing.
In particular, code should use 2 spaces for indents.

## Licence

By contributing to Jactl, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE), the same licence used by the project.
