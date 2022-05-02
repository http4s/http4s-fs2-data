ThisBuild / tlBaseVersion := "0.23"
ThisBuild / tlMimaPreviousVersions ++= (0 to 11).map(y => s"0.23.$y").toSet
ThisBuild / developers := List(
  tlGitHubDev("rossabaker", "Ross A. Baker")
)

val Scala213 = "2.13.8"
ThisBuild / crossScalaVersions := Seq("2.12.15", Scala213, "3.1.1")
ThisBuild / scalaVersion := Scala213

lazy val root = project.in(file(".")).aggregate(scalaXml).enablePlugins(NoPublishPlugin)

val http4sVersion = "0.23.11"
val scalaXmlVersion = "2.1.0"
val munitVersion = "0.7.29"
val munitCatsEffectVersion = "1.0.7"

lazy val scalaXml = project
  .in(file("scala-xml"))
  .settings(
    name := "http4s-scala-xml",
    description := "Provides scala-xml codecs for http4s",
    startYear := Some(2014),
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-core" % http4sVersion,
      "org.scala-lang.modules" %%% "scala-xml" % scalaXmlVersion,
      "org.scalameta" %%% "munit-scalacheck" % munitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect-3" % munitCatsEffectVersion % Test,
      "org.http4s" %%% "http4s-laws" % http4sVersion % Test,
    ),
  )

lazy val docs = project
  .in(file("site"))
  .dependsOn(scalaXml)
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-dsl" % http4sVersion,
      "org.http4s" %%% "http4s-circe" % http4sVersion,
      "io.circe" %%% "circe-generic" % "0.14.1",
    )
  )
  .enablePlugins(Http4sOrgSitePlugin)
