
name := "subpooling_core"
organization := "io.getblok"
version := "0.5"
idePackagePrefix := Some("io.getblok.subpooling_core")
scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "org.ergoplatform" %% "ergo-appkit" % "4.0.10",
  "org.postgresql" % "postgresql" % "42.3.4",
  "org.scalatest" %% "scalatest" % "3.2.11" % "test",
//  "org.slf4j" % "slf4j-simple" % "1.7.36"
  "io.github.getblok-io" %% "getblok_plasma" % "0.0.8"
)

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "New Sonatype Releases" at "https://s01.oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Bintray" at "https://jcenter.bintray.com/"
)

//assemblyJarName in assembly := s"subpooling-${version.value}.jar"
//mainClass in assembly := Some("app.SubpoolMain")
//assemblyOutputPath in assembly := file(s"./subpooling-${version.value}.jar/")
