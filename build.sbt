
name := "jwt-demo"

scalaVersion := "2.12.2"

val http4sVersion = "0.15.13a"
val circeVersion = "0.8.0"

libraryDependencies ++= Seq(
  "org.http4s"           %% "http4s-core"          % http4sVersion,
  "org.http4s"           %% "http4s-dsl"           % http4sVersion,
  "org.http4s"           %% "http4s-circe"         % http4sVersion,
  "org.http4s"           %% "http4s-blaze-server"  % http4sVersion,
  "io.circe"             %% "circe-core"           % circeVersion,
  "io.circe"             %% "circe-generic"        % circeVersion,
  "io.circe"             %% "circe-generic-extras" % circeVersion,
  "io.circe"             %% "circe-parser"         % circeVersion,
  "io.circe"             %% "circe-java8"          % circeVersion,
  "ch.qos.logback"        % "logback-classic"      % "1.2.3",
  "com.pauldijou"        %% "jwt-core"             % "0.13.0"
)
