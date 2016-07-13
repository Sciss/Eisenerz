lazy val baseName         = "Eisenerz-Exposure"
lazy val baseNameL        = baseName.toLowerCase
lazy val projectVersion   = "0.1.0-SNAPSHOT"

lazy val commonSettings = Seq(
  version             := projectVersion,
  organization        := "de.sciss",
  description         := "An art project",
  homepage            := Some(url(s"https://github.com/Sciss/$baseName")),
  scalaVersion        := "2.11.8",
  licenses            := Seq(gpl2),
  scalacOptions      ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint"),
  resolvers           += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/maven-releases/",
  libraryDependencies ++= Seq(
    "de.sciss"          %% "fileutil"           % "1.1.1",
    "de.sciss"          %% "numbers"            % "0.1.1",
    // "de.sciss"          %% "kollflitz"          % "0.2.0",
    // "de.sciss"          %  "submin"             % "0.2.1",
    "com.github.scopt"  %% "scopt"              % "3.4.0",
    "com.pi4j"          %  "pi4j-core"          % "1.0",
    "de.sciss"          %  "jrpicam"            % "0.1.0"
  )
)

//lazy val cc_by_nc_nd = "CC BY-NC-ND 4.0" -> url("http://creativecommons.org/licenses/by-nc-nd/4.0/legalcode")
lazy val gpl2        = "GPL v2+"         -> url("http://www.gnu.org/licenses/gpl-2.0.txt")

lazy val root = Project(id = baseNameL, base = file("."))
  .settings(commonSettings)

// -------------

mainClass in assembly := Some("de.sciss.eisenerz.Exposure")

assemblyJarName in assembly := s"$baseName.jar"
