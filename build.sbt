import NativePackagerHelper._
import com.typesafe.sbt.packager.docker.Cmd

enablePlugins(JavaAppPackaging, DockerPlugin)

name := "spikeroog"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "org.javacord" % "javacord" % "3.0.0" % Compile,

  // Dependency Injection
  "net.codingwell" %% "scala-guice" % "4.2.1",

  // JSON Support
  "io.spray" %% "spray-json" % "1.3.4",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.6",

  // CSV
  "com.github.tototoshi" %% "scala-csv" % "1.3.5",

  // Akka Http dependencies
  "com.typesafe.akka" %% "akka-http" % "10.1.3",
  "com.typesafe.akka" %% "akka-stream" % "2.5.12",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.4",

  // Logging dependencies
  "org.apache.logging.log4j" %% "log4j-api-scala" % "11.0",
  "org.apache.logging.log4j" % "log4j-core" % "2.11.1" % Runtime,
  "org.apache.logging.log4j" % "log4j-api" % "2.11.1" % Runtime,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.9.6" % Runtime,
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.6" % Runtime,

  // Test dependencies
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.scalamock" %% "scalamock" % "4.1.0" % Test
)

// Docker configuration
packageName in Docker := packageName.value
version in Docker := version.value

dockerCommands ++= Seq(
  Cmd("USER", "root"),
  Cmd("RUN", "apk", "--no-cache", "add", "bash"),
  Cmd("USER", "daemon")
)

dockerExposedPorts := List(8001)
dockerRepository := Some("nikagra")
dockerLabels := Map("maintainer" -> "nikagra@gmail.com")

dockerBaseImage := "openjdk:8-jre-alpine"
defaultLinuxInstallLocation in Docker := "/usr/local"
daemonUser in Docker := "daemon"
mappings in Universal ++= directory(baseDirectory.value / "src" / "main" / "resources")