# Building Balatry

Maven, Java 21. No JUnit — the tests are hand-rolled `*Tests` mains (see below).

## Commands

- `mvn test` — compiles everything and runs all test harnesses. A red harness fails the build.
- `mvn package` — builds the jar.
- `mvn exec:java` — runs the server main (`model.game.net.ServerMain`, once it exists).
- `mvn javafx:run` — runs the client (`client.BalatryClient`, once it exists).
- add `-DskipTests` to skip the harness run.

## How testing works

There is no JUnit dependency. Each test is a class named `*Tests` with a `public static void
main` that prints per-check results and calls `System.exit(non-zero)` on any failure — the
convention this project has used since the start.

`harness.HarnessRunner` (run in the `test` phase by the exec plugin) discovers every `*Tests`
class under `target/test-classes/model/`, launches each in its own JVM, and exits non-zero if any
harness did. Running each harness in a fresh JVM means a harness's own `System.exit` is just that
subprocess's status — nothing to intercept — and no static state leaks between harnesses.

Adding a new harness needs no registration: name it `*Tests`, give it a `main`, and the runner
finds it.

## The module descriptor is deliberately absent

There is no `module-info.java` yet. Adding one now would force JavaFX onto the module path and
require `client.BalatryClient` and `model.game.net.ServerMain` to already exist, breaking the
build before those classes are written. JavaFX 21 runs fine on the classpath for development, so
the descriptor is deferred to the first client session, when the main classes it must reference
actually exist.

## Dependencies

- `org.openjfx:javafx-controls` — the client only. The model, actions, host, and net layers have
  zero third-party dependencies and compile standalone.
- `jackson-databind` — present for the eventual `ActionCodec` swap from the hand-rolled line
  format to JSON. Not yet referenced by any source.
