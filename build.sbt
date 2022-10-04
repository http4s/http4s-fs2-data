ThisBuild / tlBaseVersion := "0.0"
ThisBuild / developers := List(
  tlGitHubDev("rossabaker", "Ross A. Baker")
)

val Scala213 = "2.13.8"
ThisBuild / crossScalaVersions := Seq("2.12.16", Scala213, "3.1.3")
ThisBuild / scalaVersion := Scala213

// ensure missing timezones don't break tests on JS
ThisBuild / jsEnv := {
  import org.scalajs.jsenv.nodejs.NodeJSEnv
  new NodeJSEnv(NodeJSEnv.Config().withEnv(Map("TZ" -> "UTC")))
}

lazy val root = tlCrossRootProject.aggregate(xml, xmlScala)

val http4sVersion = "0.23.14"
val scalaXmlVersion = "2.1.0"
val fs2DataVersion = "1.5.0+19-3dd4d2cc-SNAPSHOT"
val munitVersion = "0.7.29"
val munitCatsEffectVersion = "1.0.7"

lazy val xml = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("xml"))
  .settings(
    name := "http4s-fs2-data-xml",
    description := "Provides xml codecs for http4s via fs2-data",
    startYear := Some(2022),
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % "3.3.0-47-85f745e-20221004T141927Z-SNAPSHOT",
      "org.http4s" %%% "http4s-core" % http4sVersion,
      "org.gnieh" %%% "fs2-data-xml" % fs2DataVersion,
      "org.scalameta" %%% "munit-scalacheck" % munitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect-3" % munitCatsEffectVersion % Test,
      "org.http4s" %%% "http4s-laws" % http4sVersion % Test,
    ),
  )

lazy val xmlScala = crossProject(JVMPlatform, JSPlatform)
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
      "org.typelevel" %%% "munit-cats-effect-3" % munitCatsEffectVersion % Test,
      // "org.typelevel" %%% "scalacheck-xml" % "0.1.0" % Test,
      "org.http4s" %%% "http4s-laws" % http4sVersion % Test,
    ),
  )

lazy val docs = project
  .in(file("site"))
  .dependsOn(xml.jvm, xmlScala.jvm)
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-dsl" % http4sVersion,
      "org.http4s" %%% "http4s-circe" % http4sVersion,
      "io.circe" %%% "circe-generic" % "0.14.1",
    )
  )
  .enablePlugins(Http4sOrgSitePlugin)
