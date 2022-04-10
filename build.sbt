
name := "subpooling_service"
organization := "io.getblok"
version := "0.0.1"
maintainer := "ksingh@getblok.io"
scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "org.ergoplatform" %% "ergo-appkit" % "develop-d90135c5-SNAPSHOT",
  "org.postgresql" % "postgresql" % "42.3.3",
  "org.scalatest" %% "scalatest" % "3.2.11" % "test",
  "org.fusesource.jansi" % "jansi" % "2.4.0",
  "io.swagger" % "swagger-annotations" % "1.6.5",
   guice

)
lazy val core = Project(id = "subpooling_core", base = file("subpooling_core"))
lazy val root = Project(id = "subpooling_service", base = file(".")).enablePlugins(PlayScala).dependsOn(core)

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)
topLevelDirectory := Some("subpooling_service")
//assemblyJarName in assembly := s"subpooling-${version.value}.jar"
//mainClass in assembly := Some("app.SubpoolMain")
//assemblyOutputPath in assembly := file(s"./subpooling-${version.value}.jar/")
