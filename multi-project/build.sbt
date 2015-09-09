lazy val root = project in file(".")

lazy val core = project.in(file("core")).settings(bundleSettings(root): _*)

lazy val frontend = project.in(file("frontend")).dependsOn(core).settings(bundleSettings(root): _*)

repositoryAndRunnerSettings(core, frontend)

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.12",
  "org.slf4j" % "slf4j-api" % "1.7.12")

