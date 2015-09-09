lazy val sbtFelix = uri("https://github.com/doolse/sbt-osgi-felix.git#master")

lazy val root = project.in( file(".") ).dependsOn( sbtFelix )


