
name := "subpooling_service"
organization := "io.getblok"
version := "1.0"
maintainer := "ksingh@getblok.io"
scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "org.ergoplatform" %% "ergo-appkit" % "4.0.8",
  "org.postgresql" % "postgresql" % "42.3.4",
  "org.scalatest" %% "scalatest" % "3.2.11" % "test",
  "io.swagger" % "swagger-annotations" % "1.6.5",
   guice,
   ws,
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0",
  "com.typesafe.slick" %% "slick" % "3.3.3",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3",
  "io.github.getblok-io" %% "getblok_plasma" % "0.0.2"
)
lazy val core = Project(id = "subpooling_core", base = file("subpooling_core"))
lazy val root = Project(id = "subpooling_service", base = file(".")).enablePlugins(PlayScala).dependsOn(core)

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "New Sonatype Releases" at "https://s01.oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Bintray" at "https://jcenter.bintray.com/"
)
topLevelDirectory := Some("subpooling_service")

import com.typesafe.sbt.packager.docker.DockerChmodType
import com.typesafe.sbt.packager.docker.DockerPermissionStrategy
dockerChmodType := DockerChmodType.UserGroupWriteExecute
dockerPermissionStrategy := DockerPermissionStrategy.CopyChown
dockerEntrypoint := Seq("/opt/docker/bin/subpooling_service", "-Dconfig.file=/opt/docker/conf/mainnet.conf")

