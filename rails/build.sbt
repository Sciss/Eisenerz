lazy val baseName         = "Eisenerz-Rails"
lazy val baseNameL        = baseName.toLowerCase
lazy val projectVersion   = "0.1.0-SNAPSHOT"

lazy val commonSettings = Seq(
  version             := projectVersion,
  organization        := "de.sciss",
  description         := "An art project",
  homepage            := Some(url(s"https://github.com/Sciss/$baseName")),
  scalaVersion        := "2.11.8",
  licenses            := Seq(gpl3),
  scalacOptions      ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint"),
  resolvers           += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/maven-releases/",
  libraryDependencies ++= Seq(
    "de.sciss"          %% "scalacollider"              % "1.18.1",
    "de.sciss"          %% "fileutil"                   % "1.1.1",
    "de.sciss"          %% "numbers"                    % "0.1.1",
    "com.github.scopt"  %% "scopt"                      % "3.4.0",
    "de.sciss"          %% "pdflitz"                    % "1.2.1",
    "de.sciss"          %% "scissdsp"                   % "1.2.2"
  )
)

lazy val gpl3        = "GPL v3+"         -> url("http://www.gnu.org/licenses/gpl-3.0.txt")

lazy val root = project.in(file("."))
  .settings(commonSettings)
  .settings(
    name := baseName
  )
