name := "subpooling"

version := "0.0.1"

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "org.ergoplatform" %% "ergo-appkit" % "develop-d90135c5-SNAPSHOT",
  "org.slf4j" % "slf4j-jdk14" % "1.7.36",
  "org.postgresql" % "postgresql" % "42.3.2",
  "org.springframework.boot" % "spring-boot-starter" % "2.6.4",
  "org.springframework.boot" % "spring-boot-starter-test" % "2.6.4",
  "org.springframework.boot" % "spring-boot-starter-web" % "2.6.4",
  "org.springframework.boot" % "spring-boot-starter-parent" % "2.6.4",
  "org.springdoc" % "springdoc-openapi-ui" % "1.6.6",
  "org.springframework.boot" % "spring-boot-configuration-processor" % "2.6.4",
  "org.scalatest" %% "scalatest" % "3.2.11" % "test"
)

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

assemblyJarName in assembly := s"subpooling-${version.value}.jar"
mainClass in assembly := Some("app.SubpoolMain")
assemblyOutputPath in assembly := file(s"./subpooling-${version.value}.jar/")