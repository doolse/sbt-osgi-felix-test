lazy val root = project.in( file(".") ).dependsOn( sbtFelix )

lazy val sbtFelix = file("../sbt-osgi-felix").toURI
