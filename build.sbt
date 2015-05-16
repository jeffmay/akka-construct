lazy val commonRootSettings = Seq(
  organization := "me.jeffmay",
  organizationName := "Jeff May",
  version := "0.6.0",
  scalaVersion := "2.11.6",
  crossScalaVersions := Seq("2.11.6", "2.10.4")
)

commonRootSettings

lazy val akkaVersion = "2.3.11"

lazy val common = commonRootSettings ++ Seq(
  // force scala version
  ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
  scalacOptions := {
    // the deprecation:false flag is only supported by scala >= 2.11.3, but needed for scala >= 2.11.0 to avoid warnings
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, scalaMinor)) if scalaMinor >= 11 =>
        // For scala versions >= 2.11.3
        Seq("-Xfatal-warnings", "-deprecation:false")
      case Some((2, scalaMinor)) if scalaMinor < 11 =>
        // For scala versions 2.10.x
        Seq("-Xfatal-warnings")
    }
  } ++ Seq(
    "-feature",
    "-Xlint",
    "-Ywarn-dead-code",
    "-encoding", "UTF-8"
  ),
  libraryDependencies ++= Seq(
    "org.mockito" % "mockito-all" % "1.10.19" % "test",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "com.typesafe.akka" %% "akka-actor" % akkaVersion
  ),
  // disable compilation of ScalaDocs, since this always breaks on links and isn't as helpful as source
  sources in(Compile, doc) := Seq.empty,
  // disable publishing empty ScalaDocs
  publishArtifact in (Compile, packageDoc) := false,
  // Apache 2 licence
  licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache-2.0"))
) ++ bintraySettings ++ bintrayPublishSettings

lazy val akkaConstruct = (project in file("akkaConstruct")).settings(common: _*).settings(
  name := "akka-construct",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
  )
)

lazy val akkaConstructTest = (project in file("akkaConstructTest")).settings(common: _*).settings(
  name := "akka-construct-test",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion
  )
).dependsOn(akkaConstruct % "compile->compile;test->test")

// Disable publishing for aggregate root
publish := {}
