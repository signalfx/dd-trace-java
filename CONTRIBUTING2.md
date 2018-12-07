### New Contributing draft (to go with v1 api)

Pull requests for bug fixes are welcome, but before submitting new features or changes to current functionality [open an issue](https://github.com/DataDog/dd-trace-java/issues/new)
and discuss your ideas or propose the changes you wish to make. After a resolution is reached a PR can be submitted for review.

### Notable Projects and Dependencies
See [settings.gradle](settings.gradle) for all projects and general descriptions.

#### Core Tracing Libraries
![Alt text](https://g.gravizo.com/source/custom_mark10?https%3A%2F%2Fgithub.com%2FDataDog%2Fdd-trace-java%2Fblob%2Fark%2Ftesting-markdown%2FCONTRIBUTING2.md)
<details>
<summary>Tracing libraries and their dependencies</summary>
custom_mark10
  digraph G {
    foo -> bar;
    foo -> foo2;
    bar -> baz;
  }
custom_mark10
</details>

#### Java Agent and automatic integrations

### Code Style

This project includes a `.editorconfig` file for basic editor settings.  This file is supported by most common text editors.

Java files must be formatted using [google-java-format](https://github.com/google/google-java-format).  Please run the following task to ensure files are formatted before committing:

```shell
./gradlew :googleJavaFormat
```

Other source files (Groovy, Scala, etc) should ideally be formatted by Intellij Idea's default formatting, but are not enforced.
