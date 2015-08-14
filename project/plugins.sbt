lazy val root = project.in( file(".") ).dependsOn( sbtFelix )

lazy val sbtFelix = file("../sbt-osgi-felix").toURI

addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.7.0")
