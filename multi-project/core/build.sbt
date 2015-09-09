import com.typesafe.sbt.osgi.OsgiKeys._

osgiDependencies in Compile := packageReqs("org.slf4j")

exportPackage += "com.doolse.core"
