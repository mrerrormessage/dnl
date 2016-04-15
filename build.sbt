enablePlugins(org.nlogo.build.NetLogoExtension)

scalaVersion           := "2.11.7"

scalaSource in Compile := { baseDirectory.value / "src" / "main" }

scalaSource in Test    := { baseDirectory.value / "src" / "test" }

javaSource in Compile  := { baseDirectory.value / "src" / "main"}

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings",
                        "-encoding", "us-ascii")

javacOptions ++= Seq("-g", "-deprecation", "-Xlint:all", "-encoding", "us-ascii")

name := """distributed-netlogo"""

libraryDependencies ++= Seq(
  "org.zeromq" % "jeromq" % "0.3.5",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)

netLogoClassManager := "org.nlogo.extensions.dnl.DistributedNetLogoExtension"

netLogoExtName      := "dnl"

netLogoZipSources   := false

netLogoVersion := "6.0.0-M4"
