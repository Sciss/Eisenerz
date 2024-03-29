lazy val baseName         = "Eisenerz-Experiments"
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
    "de.sciss"          %% "processor"          % "0.4.0",
    "com.mortennobel"   %  "java-image-scaling" % "0.8.6",  // includes jh filters
    "de.sciss"          %% "audiowidgets-swing" % "1.10.0",
    "de.sciss"          %% "desktop"            % "0.7.2",
    "de.sciss"          %% "guiflitz"           % "0.5.0",
    "de.sciss"          %% "play-json-sealed"   % "0.4.0",
    "de.sciss"          %% "kollflitz"          % "0.2.0",
    // "de.sciss"          %  "submin"             % "0.2.1",
    "com.github.scopt"  %% "scopt"              % "3.4.0",
    "de.sciss"          %% "scissdsp"           % "1.2.2",
    "de.sciss"          %% "scalaaudiofile"     % "1.4.5",
    "de.sciss"          %% "fscapejobs"         % "1.5.0"
  )
)

//lazy val cc_by_nc_nd = "CC BY-NC-ND 4.0" -> url("http://creativecommons.org/licenses/by-nc-nd/4.0/legalcode")
lazy val gpl2        = "GPL v2+"         -> url("http://www.gnu.org/licenses/gpl-2.0.txt")

lazy val root = project.in(file("."))
  .settings(commonSettings)
  .settings(
    name := baseName
  )

// -------------

//mainClass in assembly := Some("de.sciss.unlike.Main")
//
//assemblyJarName in assembly := s"$baseName.jar"
