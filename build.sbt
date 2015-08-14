import sbt.Package.ManifestAttributes

scalaVersion := "2.9.2"

scalaSource in Compile := { baseDirectory.value / "src" / "main" }

scalaSource in Test    := { baseDirectory.value / "src" / "test" }

javaSource in Compile  := { baseDirectory.value / "src" / "main"}

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings",
                        "-encoding", "us-ascii")

javacOptions ++= Seq("-g", "-deprecation", "-Xlint:all", "-encoding", "us-ascii")

name := """distributed-netlogo"""

libraryDependencies ++= Seq(
  "org.nlogo" % "NetLogo" % "5.2.0" from "http://ccl.northwestern.edu/netlogo/5.2/NetLogo.jar",
  "org.zeromq" % "jeromq" % "0.3.5",
  "org.scalatest" %% "scalatest" % "1.8" % "test"
)

enablePlugins(org.nlogo.build.NetLogoExtension)

netLogoClassManager := "DistributedNetLogoExtension"

netLogoExtName      := "dnl"

netLogoZipSources   := false


fork in run := true
