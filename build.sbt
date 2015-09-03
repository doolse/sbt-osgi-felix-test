import com.typesafe.sbt.osgi.OsgiKeys._
import osgifelix.OsgiFelixPlugin.autoImport._

defaultSingleProjectSettings

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "org.elasticsearch" % "elasticsearch" % "1.2.1",
  "com.sonian" % "elasticsearch-zookeeper" % "1.2.0",
  "org.slf4j" % "slf4j-simple" % "1.7.12",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.12",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.12",
  "org.apache.zookeeper" % "zookeeper" % "3.4.6")

lazy val scalacheck = "org.scalacheck" %% "scalacheck" % "1.12.0"

libraryDependencies += scalacheck % Test

osgiRepositoryRules := Seq(
  rewrite("zookeeper", imports = "org.ieft.jgss.*,org.apache.log4j.jmx.*;resolution:=optional,*"),
  create("elasticsearch" | "lucene*", symbolicName = "elasticsearch", version = "1.2.1",
    imports = "com.vividsolutions.jts.*;resolution:=optional,org.hyperic.sigar;resolution:=optional,org.apache.regexp;resolution:=optional,*",
    exports = "org.apache.lucene.*,org.elasticsearch.*,org.tartarus.snowball.*"),
  ignoreAll("log4j", "slf4j-log4j12")
)

osgiDependencies in Compile := packageReqs("org.elasticsearch.client")

bundleActivator := Some("testbundle.OsgiBundle")

exportPackage += "testbundle"
