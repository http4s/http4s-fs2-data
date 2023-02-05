ThisBuild / tlBaseVersion := "0.1"
ThisBuild / developers := List(
  tlGitHubDev("rossabaker", "Ross A. Baker"),
  tlGitHubDev("ybasket", "Yannick Heiber"),
)

val Scala213 = "2.13.10"
ThisBuild / crossScalaVersions := Seq(Scala213, "3.2.2")
ThisBuild / scalaVersion := Scala213

// ensure missing timezones don't break tests on JS
ThisBuild / jsEnv := {
  import org.scalajs.jsenv.nodejs.NodeJSEnv
  new NodeJSEnv(NodeJSEnv.Config().withEnv(Map("TZ" -> "UTC")))
}

lazy val root = tlCrossRootProject.aggregate(xml, xmlScala)

val http4sVersion = "1.0.0-M39"
val scalaXmlVersion = "2.1.0"
val fs2Version = "3.5.0"
val fs2DataVersion = "1.6.1"
val munitVersion = "1.0.0-M7"
val munitCatsEffectVersion = "2.0.0-M3"

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
