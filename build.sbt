ThisBuild / tlBaseVersion := "0.4"
ThisBuild / tlJdkRelease := Some(11)
// exclude Java 8 from CI as fs2-data doesn't support it
ThisBuild / githubWorkflowJavaVersions -= JavaSpec.temurin("8")
ThisBuild / developers := List(
  tlGitHubDev("rossabaker", "Ross A. Baker"),
  tlGitHubDev("ybasket", "Yannick Heiber"),
)

val Scala213 = "2.13.13"
ThisBuild / crossScalaVersions := Seq("2.12.19", Scala213, "3.3.3")
ThisBuild / scalaVersion := Scala213

// ensure missing timezones don't break tests on JS
ThisBuild / jsEnv := {
  import org.scalajs.jsenv.nodejs.NodeJSEnv
  new NodeJSEnv(NodeJSEnv.Config().withEnv(Map("TZ" -> "UTC")))
}

// ensure we don't fail compilation – package objects with inheritance are used in http4s/http4s as well,
// better to stay style-consistent for now
ThisBuild / scalacOptions += "-Wconf:msg=package object inheritance is deprecated:s"

lazy val root = tlCrossRootProject.aggregate(xml, xmlScala, csv, cbor, json)

val http4sVersion = "0.23.28"
val scalaXmlVersion = "2.2.0"
val fs2Version = "3.10.2"
val fs2DataVersion = "1.10.0"
val munitVersion = "1.0.0-M11"
val munitCatsEffectVersion = "2.0.0"

lazy val xml = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("xml"))
  .settings(
    name := "http4s-fs2-data-xml",
    description := "Provides xml codecs for http4s via fs2-data",
    startYear := Some(2022),
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % fs2Version,
      "org.http4s" %%% "http4s-core" % http4sVersion,
      "org.gnieh" %%% "fs2-data-xml" % fs2DataVersion,
      "org.scalameta" %%% "munit-scalacheck" % munitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectVersion % Test,
      "org.http4s" %%% "http4s-laws" % http4sVersion % Test,
    ),
  )

lazy val xmlScala = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("xml-scala"))
  .dependsOn(xml)
  .settings(
    name := "http4s-fs2-data-xml-scala",
    description := "Provides scala-xml codecs for http4s via fs2-data",
    startYear := Some(2022),
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-core" % http4sVersion,
      "org.scala-lang.modules" %%% "scala-xml" % scalaXmlVersion,
      "org.gnieh" %%% "fs2-data-xml-scala" % fs2DataVersion,
      "org.scalameta" %%% "munit-scalacheck" % munitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectVersion % Test,
      // "org.typelevel" %%% "scalacheck-xml" % "0.1.0" % Test,
      "org.http4s" %%% "http4s-laws" % http4sVersion % Test,
    ),
  )

lazy val csv = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("csv"))
  .settings(
    name := "http4s-fs2-data-csv",
    description := "Provides csv codecs for http4s via fs2-data",
    startYear := Some(2023),
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % fs2Version,
      "org.http4s" %%% "http4s-core" % http4sVersion,
      "org.gnieh" %%% "fs2-data-csv" % fs2DataVersion,
      "org.gnieh" %%% "fs2-data-csv-generic" % fs2DataVersion % Test,
      "org.scalameta" %%% "munit-scalacheck" % munitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectVersion % Test,
      "org.http4s" %%% "http4s-laws" % http4sVersion % Test,
    ),
  )

lazy val cbor = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("cbor"))
  .settings(
    name := "http4s-fs2-data-cbor",
    description := "Provides CBOR codecs for http4s via fs2-data",
    startYear := Some(2023),
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % fs2Version,
      "org.http4s" %%% "http4s-core" % http4sVersion,
      "org.gnieh" %%% "fs2-data-cbor" % fs2DataVersion,
      "org.scalameta" %%% "munit-scalacheck" % munitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectVersion % Test,
      "org.http4s" %%% "http4s-laws" % http4sVersion % Test,
    ),
  )

lazy val json = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("json"))
  .settings(
    name := "http4s-fs2-data-json",
    description := "Provides JSON codecs for http4s via fs2-data",
    startYear := Some(2024),
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % fs2Version,
      "org.http4s" %%% "http4s-core" % http4sVersion,
      "org.gnieh" %%% "fs2-data-json" % fs2DataVersion,
      "org.scalameta" %%% "munit-scalacheck" % munitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectVersion % Test,
      "org.http4s" %%% "http4s-laws" % http4sVersion % Test,
    ),
  )

lazy val docs = project
  .in(file("site"))
  .dependsOn(xml.jvm, xmlScala.jvm, csv.jvm, cbor.jvm, json.jvm)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-generic" % "0.14.5",
      "org.http4s" %%% "http4s-dsl" % http4sVersion,
      "org.http4s" %%% "http4s-circe" % http4sVersion,
      "org.gnieh" %%% "fs2-data-csv-generic" % fs2DataVersion,
    )
  )
  .enablePlugins(Http4sOrgSitePlugin)
