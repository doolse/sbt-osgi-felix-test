import com.typesafe.sbt.osgi.OsgiKeys._

osgiDependencies in Compile := packageReqs("org.osgi.framework")

bundleActivator := Some("com.doolse.frontend.Activator")

exportPackage += "com.doolse.frontend"
