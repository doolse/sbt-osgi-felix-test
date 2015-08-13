import org.apache.felix.bundlerepository.Repository
import org.osgi.framework.{Version, VersionRange}
import osgifelix._
import osgifelix.InstructionFilter._
import osgifelix.BundleInstructions._
import sbt._
import sbt.Keys._
import scalaz.Id.Id

object GenJars extends Build {

  def toConfig(config: Configuration, deps: Seq[ModuleID]): Seq[ModuleID] = deps.map {
    m => m.copy(configurations = Some(config.name))
  }

  lazy val testIt = taskKey[Seq[File]]("Test bundle creation")
  lazy val createRepo = taskKey[File]("Create a OBR repo")
  lazy val origUpdate = taskKey[UpdateReport]("Original update report")
  lazy val osgiRepo = taskKey[Repository]("Repository for resolving osgi dependencies")
  lazy val osgiInstructions = taskKey[Seq[BundleInstructions]]("Instructions for BND")
  lazy val osgiDependencies = settingKey[Seq[OsgiRequirement]]("OSGi dependencies")
  lazy val osgiFilterRules = settingKey[Seq[InstructionFilter]]("Filters for generating BND instructions")

  lazy val Osgi = config("osgi")
  lazy val FelixBundles = config("felixbundles")

  lazy val repoAdminTask = Def.task {
    val bundleJars = origUpdate.value.matching(configurationFilter(FelixBundles.name) && artifactFilter(`type` = "bundle"))
    FelixRepositories.runRepoAdmin(bundleJars)
  }

  lazy val cachedRepoLookup = Def.taskDyn[Repository] {
    val instructions = osgiInstructions.value
    val cacheFile = target.value / "bundle.cache"
    val binDir = target.value / "bundles"
    val indexFile = target.value / "index.xml"
    val cacheData = BndManifestGenerator.serialize(instructions)
    if (cacheFile.exists() && indexFile.exists() && IO.read(cacheFile) == cacheData) {
      Def.task {
        repoAdminTask.value(_.loadRepository(indexFile))
      }
    } else Def.task {
      IO.delete(binDir)
      IO.delete(cacheFile)
      IO.createDirectory(binDir)
      val logger = streams.value.log
      val builder = BndManifestGenerator.buildJars[Id](instructions, binDir)
      val jars = builder.run(new BundleProgress[Id] {
        override def info(msg: String): Id[Unit] = {
          logger.info(msg)
        }
      }).map(_.jf).toSeq
      repoAdminTask.value { repoAdmin =>
        val repo = repoAdmin.createIndexedRepository(jars)
        val reasons = repoAdmin.checkConsistency(repo)
        if (reasons.isEmpty)
        {
          IO.write(cacheFile, cacheData)
          repoAdmin.writeRepo(repo, indexFile)
          repo
        } else {
          reasons.foreach {
            r => logger.error {
              val req = r.getRequirement
              s"Failed to find ${req.getName} with ${req.getFilter} for ${r.getResource.getSymbolicName} "
            }
          }
          sys.error("Failed consistency check")
        }
      }
    }
  }

  lazy val root = (project in new File(".")).settings(
    scalaVersion := "2.11.7",
    ivyConfigurations ++= Seq(Osgi, FelixBundles),
    libraryDependencies += "org.apache.felix" % "org.apache.felix.bundlerepository" % "2.0.4" % FelixBundles,
    origUpdate := Classpaths.updateTask.value,
    libraryDependencies ++= {
      val akkaV = "2.3.11"
      val streamV = "1.0-RC4"
      toConfig(Osgi, Seq(
        "com.typesafe.akka" %% "akka-stream-experimental" % streamV,
        "com.typesafe.akka" %% "akka-http-core-experimental" % streamV,
        "com.typesafe.akka" %% "akka-http-experimental" % streamV,
        "com.typesafe.akka" %% "akka-actor" % akkaV,
        "com.typesafe.akka" %% "akka-osgi" % akkaV,
        "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
        "org.specs2" %% "specs2-core" % "2.3.11" % "test",
        "io.argonaut" %% "argonaut" % "6.1",
        "org.xerial.snappy" % "snappy-java" % "1.1.1.7",
        "org.eclipse.equinox" % "registry" % "3.5.400-v20140428-1507",
        "org.eclipse.tycho" % "org.eclipse.osgi" % "3.10.100.v20150529-1857"
      ) ++ epsDeps)
    },
    classifiersModule := {
      GetClassifiersModule("ok" % "ok" % "1.2", Seq.empty, Seq.empty, Seq.empty)
    },
    osgiInstructions := {
      Seq(manifestOnly("com.eps.sunjdk", "1.0.0", Map(
        "Fragment-Host" -> "system.bundle; extension:=framework",
        "Export-Package" -> "sun.reflect,sun.reflect.generics.reflectiveObjects,com.sun.jna,com.sun,sun.misc,com.sun.jdi,com.sun.jdi.connect,com.sun.jdi.event,com.sun.jdi.request,sun.nio.ch,com.sun.javadoc,com.sun.tools.javadoc"
      )))
    },
    osgiInstructions ++= {
      val ordered = SbtUtils.orderedDependencies(origUpdate.value.configuration("osgi").get)
      val typeFilter: NameFilter = "jar" | "bundle"
      val artifacts = ordered.flatMap { mr =>
        mr.artifacts.collectFirst {
          case (artifact, file) if typeFilter.accept(artifact.`type`) => (mr.module, artifact, file)
        }
      }
      val rules = osgiFilterRules.value
      val (unused, insts) = OsgiTasks.convertToInstructions("com.eps.wrapper.", artifacts, rules)
      if (unused.nonEmpty) {
        val logger = streams.value.log
        unused.foreach { r =>
          logger.warn(s"OSGi filter rule '${r}' is not used")
        }
      }
      insts
    },
    osgiRepo := cachedRepoLookup.value,
    osgiDependencies := Seq(PackageRequirement("argonaut"), BundleRequirement("org.scalaz.core", Some(new VersionRange("(7.1,8.0]")))),
    osgiFilterRules := Seq(
      ignoreAll("globalIgnores", "slf4j-log4j12", "slf4j-jcl", "log4j", "xercesImpl", "jsr311-api", "jsr305", "activation", "commons-logging", "apache-mime4j-benchmark"),
      rewrite("dom4j", "javax.xml.parsers", "*"),
      rewriteFilter("xstream", moduleFilter(name = "xstream", revision = "1.4.4"), imports = "net.sf.cglib.*;resolution:=optional,nu.xom;resolution:=optional,org.codehaus.jettison.*;resolution:=optional,org.jdom.*;resolution:=optional,org.kxml2.*;resolution:=optional,com.bea.xml.*;resolution:=optional,com.ctc.wstx.stax;resolution:=optional,*"),
      rewrite("kafka_2.11", "joptsimple;resolution:=optional,*", "*"),
      rewrite("reflections", "javax.servlet;resolution:=optional,org.apache.commons.vfs2;resolution:=optional,com.google.gson;resolution:=optional,*", "*"),
      rewrite("tomcat-embed-logging-log4j", "javax.servlet,org.apache.avalon.framework.logger;resolution:=optional,org.apache.log;resolution:=optional,org.apache.log4j", "*"),
      rewrite("cassandra-driver-core", "net.jpountz.lz4;resolution:=optional,org.jboss.logging;resolution:=optional,org.jboss.marshalling;resolution:=optional,com.google.protobuf;resolution:=optional,*", "*"),
      rewrite("jackson-module-scala_2.11"),
      rewrite("swagger-core_2.11"),
      rewrite("commons-configuration", imports = "javax.mail.*;resolution:=optional,org.apache.commons.jxpath.*;resolution:=optional,org.apache.tools.ant.*;resolution:=optional,*"),
      rewrite("aws-java-sdk-core", imports = "com.sun.*;resolution:=optional,org.apache.http.conn.routing,*"),
      rewrite("aws-java-sdk-s3", imports = "com.amazonaws.auth,*", exports = "!com.amazonaws.auth.*,*"),
      rewrite("jdom", imports = "org.apache.xerces.*;resolution:=optional,org.jaxen.*;resolution:=optional,oracle.xml.parser.*;resolution:=optional,*"),
      rewrite("jdom2", imports = "org.jaxen.*;resolution:=optional,*"),
      rewrite("bcmail-jdk15", imports = "javax.mail.*;resolution:=optional,*"),
      rewrite("js", imports = "org.apache.xmlbeans.*;resolution:=optional,*"),
      ignore("async-http-servlet-3.0"),
      rewriteCustom("elasticsearch-zookeeper", ManifestInstructions(fragment = Some("elasticsearch"))),
      rewrite("zookeeper", imports = "org.ieft.jgss.*,org.apache.log4j.jmx.*;resolution:=optional,*"),
      rewriteCustom("logstash-logback-encoder", ManifestInstructions(fragment = Some("ch.qos.logback.classic"), imports = "ch.qos.logback.access.*;resolution:=optional,*",
        exports = "net.logstash.logback.marker,net.logstash.logback.encoder,net.logstash.logback.appender")),
      create("elasticsearch" | "lucene*", symbolicName = "elasticsearch", version = "1.2.1", "com.vividsolutions.jts.*;resolution:=optional,org.hyperic.sigar;resolution:=optional,org.apache.regexp;resolution:=optional,*", "org.apache.lucene.*,org.elasticsearch.*,org.tartarus.snowball.*"),

      create("xpp3_min" | "xmlpull", symbolicName = "com.eps.wrapper.xmlpull", version = "1.0.0", exports = "org.xmlpull.*"),
      create("tomcat-embed-core", symbolicName = "tomcat-embed-core", version = "8.0.9", "org.apache.tomcat.spdy;resolution:=optional,javax.ejb;resolution:=optional,javax.persistence;" +
        "resolution:=optional,javax.servlet.jsp.tagext;resolution:=optional,javax.mail.internet;resolution:=optional,javax.mail;resolution:=optional,*",
        "org.apache.catalina.*,org.apache.tomcat.*,org.apache.coyote.*,org.apache.naming.*"),
      createCustom("resteasy-jaxrs", symbolicName = "resteasy-jaxrs", version = "1.0.0", instructions = ManifestInstructions(imports = "Acme.Serve;resolution:=optional,org.junit;resolution:=optional,*", privates = "javax.annotation;from:=jaxrs*", exports = "org.jboss.resteasy.*,META-INF.services;from:=resteasy*")),
      create("tomcat-embed-core", symbolicName = "servlet-api", version = "3.0", exports = "javax.servlet.*"),
      create("jaxrs-api", symbolicName = "jaxrs-api", version = "2.0.0", "*", "javax.ws.*"),
      create("json4s-core_2.11" | "json4s-ast_2.11", symbolicName = "json4s-core_2.11", version = "1.0.0", exports = "!org.json4s.jackson.*,org.json4s.*"),
      create("hamcrest*", symbolicName = "com.eps.hamcrest", version = "1.3.0.SNAPSHOT", exports = "org.hamcrest.*;version=VERSION"),
      createCustom("logback-classic", symbolicName = "shitty-logback-fragment", version = "4.2", processDefault = true, ManifestInstructions(fragment = Some("slf4j.api"), exports = "org.slf4j.helpers;from:=logstash-logback-encoder*")),
      create("jboss-annotations-*", symbolicName = "javax.annotation.security", version = "1.2", exports = "javax.annotation.security;from:=jboss-annotations-*"),

      // Below is for tika/poi
      rewrite("boilerpipe", imports = "org.apache.xerces.*;resolution:=optional,org.cyberneko.html.xercesbridge;resolution:=optional,*"),
      create("tika-parsers" | "netcdf" | "isoparser" | "jmatio", symbolicName = "apache-poi", version = "1.7.0", exports = "org.apache.tika.parser.iwork,org.apache.tika.parser.pkg,org.apache.tika.parser.mbox,org.apache.tika.parser.html,org.apache.tika.parser.rtf,org.apache.tika.parser.txt,org.apache.tika.parser.pdf,org.apache.tika.parser.microsoft,org.apache.tika.parser.microsoft.ooxml"),
      create("poi" | "poi-ooxml" | "poi-scratchpad", symbolicName = "apache-poi", version = "3.11", imports = "org.apache.xml.security.*;resolution:=optional,org.bouncycastle.*;resolution:=optional,org.apache.jcp.xml.dsig.internal.dom;resolution:=optional,*", exports = "org.apache.poi.*"),
      rewrite("poi-ooxml-schemas", imports = "org.openxmlformats.schemas.officeDocument.x2006.math;resolution:=optional,org.openxmlformats.schemas.schemaLibrary.x2006.main;resolution:=optional,schemasMicrosoftComOfficePowerpoint;resolution:=optional,schemasMicrosoftComOfficeWord;resolution:=optional,*"),
      rewrite("xmlbeans", exports = "!org.w3c.dom,*", imports = "org.apache.xml.resolver.*;resolution:=optional,org.apache.crimson.*;resolution:=optional,org.apache.tools.ant.*;resolution:=optional,org.apache.xmlbeans.impl.xpath.saxon;resolution:=optional,org.apache.xmlbeans.impl.xquery.saxon;resolution:=optional,*")
    ),
    update := {
      val updateReport = origUpdate.value
      val cached = target.value / "update.cache"
      val repo = osgiRepo.value
      val deps = osgiDependencies.value
      val resolved = repoAdminTask.value { repoAdmin =>
        repoAdmin.resolveRequirements(repo, deps)
      }.leftMap {
        _.foreach(r => System.err.println(r.getRequirement))
      }.getOrElse(Seq.empty)
      val osgiModules = resolved.map { f =>
        val mr = ModuleReport("osgi" % f.getName % "1.0", Seq(Artifact(f.getName) -> f), Seq.empty)
        OrganizationArtifactReport("osgi", f.getName, Seq(mr))
      }
      val configReport = updateReport.configuration("scala-tool").get
      val allNewModules = osgiModules.flatMap(_.modules) ++ configReport.modules
      val allOrgReportts = configReport.details ++ osgiModules
      val newConfig = new ConfigurationReport("compile", allNewModules, allOrgReportts, Seq.empty)
      val newConfigs = Seq(newConfig) ++ (updateReport.configurations.filter(r => !Set("compile", "runtime", "compile-internal", "runtime-internal").contains(r.configuration)))
      new UpdateReport(cached, newConfigs, new UpdateStats(0, 0, 0, false), Map.empty)
    }
  )


  def epsDeps = Seq(
    "com.google.inject" % "guice" % "4.0-beta5",
    "com.google.inject.extensions" % "guice-assistedinject" % "4.0-beta5",
    "com.google.guava" % "guava" % "18.0",

    "org.scala-lang" % "scala-library" % "2.11.6",
    "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.3",

    "org.apache.tomcat.embed" % "tomcat-embed-core" % "8.0.9",
    "org.apache.tomcat.embed" % "tomcat-embed-logging-log4j" % "8.0.9",

    "org.slf4j" % "slf4j-api" % "1.7.12",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.12",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.12",
    "ch.qos.logback" % "logback-core" % "1.1.3",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "net.logstash.logback" % "logstash-logback-encoder" % "4.2",

    "commons-codec" % "commons-codec" % "1.10",
    "commons-beanutils" % "commons-beanutils" % "1.9.2",
    "org.apache.commons" % "commons-compress" % "1.9",

    "org.apache.zookeeper" % "zookeeper" % "3.4.6",
    "com.netflix.curator" % "curator-recipes" % "1.3.3",
    "com.101tec" % "zkclient" % "0.3",

    "org.ow2.asm" % "asm" % "4.1",
    "org.ow2.asm" % "asm-util" % "4.1",
    "org.ow2.asm" % "asm-tree" % "4.1",
    "org.ow2.asm" % "asm-analysis" % "4.1",

    "com.eps" % "anti-xml_2.11" % "0.5.3-8",

    "org.jboss.resteasy" % "resteasy-jaxrs" % "3.0.11.Final",
    "org.jboss.resteasy" % "async-http-servlet-3.0" % "3.0.11.Final",

    "com.fasterxml.jackson.core" % "jackson-core" % "2.5.4",
    "com.fasterxml.jackson.core" % "jackson-annotations" % "2.5.4",
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.5.4",
    "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-json-provider" % "2.5.4",
    "com.fasterxml.jackson.module" % "jackson-module-jsonSchema" % "2.5.4",

    "com.wordnik" % "swagger-core_2.11" % "1.3.12",
    "com.wordnik" % "swagger-jaxrs_2.11" % "1.3.12",
    "com.wordnik" % "swagger-annotations" % "1.3.12",

    "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.7.1",
    "org.hdrhistogram" % "HdrHistogram" % "2.1.4",


    "org.scalaz" % "scalaz-core_2.11" % "7.1.2",
    "org.scalaz" % "scalaz-effect_2.11" % "7.1.2",

    "org.hurl" % "hurl" % "1.1",

    "junit" % "junit" % "4.12",

    "com.pearson.statsdclient" % "statsdclient" % "1.0.0",

    "com.pearson.eps.kafka" % "kafka_2.11" % "0.8.1.eps-6",

    "com.pearson.seer" % "seer-restexpress-client" % "0.1.7",
    "com.ecollege.javariddler" % "java-riddler" % "1.1",
    "com.pearson.subpub" % "subpub-java" % "0.3.2",

    "com.amazonaws" % "aws-java-sdk-s3" % "1.10.2",

    "org.apache.httpcomponents" % "httpcore" % "4.3",
    "org.apache.httpcomponents" % "httpclient" % "4.3.1",
    "org.apache.httpcomponents" % "httpasyncclient" % "4.0",

    "rhino" % "js" % "1.7R2",

    "net.databinder.dispatch" % "dispatch-core_2.11" % "0.11.2",
    "redis.clients" % "jedis" % "2.1.0",
    "com.officedrop" % "jedis-failover-equella" % "0.1.16.3",

    "org.elasticsearch" % "elasticsearch" % "1.2.1",
    "com.sonian" % "elasticsearch-zookeeper" % "1.2.0",

    "org.apache.tika" % "tika-core" % "1.7",
    "org.apache.tika" % "tika-parsers" % "1.7",
    "org.jdom" % "jdom2" % "2.0.4",
    "dom4j" % "dom4j" % "1.6.1",

    "org.hamcrest" % "hamcrest-library" % "1.3",
    "org.scalacheck" % "scalacheck_2.11" % "1.12.3",
    "net.databinder.dispatch" % "dispatch-core_2.11" % "0.11.2",
    "com.github.scopt" % "scopt_2.11" % "3.3.0"
  )
}