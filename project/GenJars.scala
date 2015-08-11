import osgifelix._
import sbt._
import sbt.Keys._
import scalaz.Id.Id

object GenJars extends Build {

  def toConfig(config: Configuration, deps: Seq[ModuleID]): Seq[ModuleID] = deps.map {
    m => m.copy(configurations = Some(config.name))
  }

  lazy val testIt = taskKey[Seq[File]]("Test bundle creation")
  lazy val createRepo = taskKey[File]("Create a OBR repo")

  lazy val Osgi = config("osgi")
  lazy val FelixBundles = config("felixbundles")
  lazy val root = (project in new File(".")).settings(
    scalaVersion := "2.11.7",
    ivyConfigurations ++= Seq(Osgi, FelixBundles),
    libraryDependencies += "org.apache.felix" % "org.apache.felix.bundlerepository" % "2.0.4" % FelixBundles,
    managedClasspath in Compile := Attributed.blankSeq(testIt.value),
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
        "org.eclipse.equinox" % "registry" % "3.5.400-v20140428-1507"
      )) // ++ epsDeps)
    },
    createRepo := {
      val indexFile = target.value / "index.xml"
      val bundles = testIt.value
      val bundleJars = update.value.matching(configurationFilter(FelixBundles.name) && artifactFilter(`type` = "bundle"))
      FelixRepositories.runRepoAdmin(bundleJars) { repoAdmin =>
        val repo = repoAdmin.createIndexedRepository(bundles)
        repoAdmin.writeRepo(repo, indexFile)
      }
      indexFile
    }

    ,
    testIt := {
      val binDir = target.value / "bundles"
      val srcsDir = target.value / "bundle-srcs"
      IO.delete(Seq(binDir, srcsDir))
      IO.createDirectories(Seq(binDir, srcsDir))
      val ordered = SbtUtils.orderedDependencies(update.value.configuration("osgi").get)
      val typeFilter: NameFilter = "jar" | "bundle"
      val artifacts = ordered.flatMap { mr =>
        mr.artifacts.collectFirst {
          case (artifact, file) if typeFilter.accept(artifact.`type`) => (mr.module, artifact, file)
        }
      }
      val files = artifacts.map {
        case (moduleId, artifact, file) =>
          val (jar, deets) = BndManifestGenerator.parseDetails(file)
          deets.map(_ => CopyBundle(file, jar, Nil)).getOrElse {
            RewriteManifest(jar, "com.test." + moduleId.name, SbtUtils.convertRevision(moduleId.revision), Nil, ManifestInstructions.Default)
          }
      }
      val logger = streams.value.log
      val builder = BndManifestGenerator.buildJars[Id](files, binDir, srcsDir)
      val jars = builder.run(new BundleProgress[Id] {
        override def info(msg: String): Id[Unit] = {
          logger.info(msg)
        }
      })
      jars.map(_.jf).toSeq
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