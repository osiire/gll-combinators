lazy val root = project.in(file("."))
  .aggregate(coreJVM, coreJS)
  .settings(
    skip in publish := true,
    publish := (()),
    publishLocal := (()),
    publishArtifact := false,
    publishTo := None)

lazy val core = crossProject
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "gll-combinators",

    organization := "com.codecommit",

    version := "2.4-SNAPSHOT",

    licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php")),

    homepage := Some(url("https://github.com/djspiewak/gll-combinators")),

    parallelExecution in Test := false,

    libraryDependencies ++= Seq(
      "org.scalacheck" %%% "scalacheck"        % "1.13.4" % "test",

      "org.specs2"     %%% "specs2-core"       % "4.0.3"  % "test",
      "org.specs2"     %%% "specs2-scalacheck" % "4.0.3"  % "test"),

    scalacOptions += "-language:_",

    scalacOptions in Test ++= Seq("-Yrangepos"),

    logBuffered := false,       // gives us incremental output from Specs2

    unmanagedSourceDirectories in Test += baseDirectory.value / "examples" / "src",

    unmanagedResourceDirectories in Test += baseDirectory.value / "examples" / "input",

    publishArtifact in (Compile, packageDoc) := true,

    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (version.value.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },

    publishMavenStyle := true,

    publishArtifact in Test := false,

    pomIncludeRepository := { _ => false },

    pomExtra := (
      <scm>
        <url>git://github.com/djspiewak/gll-combinators.git</url>
        <connection>scm:git:git://github.com/djspiewak/gll-combinators.git</connection>
      </scm>
      <developers>
        <developer>
          <id>djspiewak</id>
          <name>Daniel Spiewak</name>
          <url>http://www.codecommit.com/blog</url>
        </developer>
      </developers>),

    sonatypeProfileName := "com.codecommit",

    autoAPIMappings := true,

    scalacOptions in (Compile, doc) ++= Seq("-groups", "-implicits"))

lazy val coreJVM = core.jvm
lazy val coreJS = core.js
