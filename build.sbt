import com.typesafe.sbt.osgi.OsgiKeys._

defaultSingleProjectSettings ++ deploymentSettings

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "org.elasticsearch" % "elasticsearch" % "1.2.1",
  "com.sonian" % "elasticsearch-zookeeper" % "1.2.0",
  "org.slf4j" % "slf4j-simple" % "1.7.12",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.12",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.12",
  "org.apache.zookeeper" % "zookeeper" % "3.4.6")

osgiFilterRules := Seq(
  rewrite("zookeeper", imports = "org.ieft.jgss.*,org.apache.log4j.jmx.*;resolution:=optional,*"),
  ignoreAll("globalIgnores", "log4j", "slf4j-log4j12"),
  create("elasticsearch" | "lucene*", symbolicName = "elasticsearch", version = "1.2.1",
    imports = "com.vividsolutions.jts.*;resolution:=optional,org.hyperic.sigar;resolution:=optional,org.apache.regexp;resolution:=optional,*",
    exports = "org.apache.lucene.*,org.elasticsearch.*,org.tartarus.snowball.*")
)

osgiDependencies := packageReqs("org.elasticsearch.client")

bundleActivator := Some("testbundle.OsgiBundle")

exportPackage += "testbundle"
