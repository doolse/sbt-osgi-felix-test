package testbundle

import org.osgi.framework.{BundleContext, BundleActivator}
import org.slf4j.LoggerFactory

/**
 * Created by jolz on 24/08/15.
 */
class OsgiBundle extends BundleActivator {

  val logger = LoggerFactory.getLogger(getClass)


  override def stop(context: BundleContext): Unit = {

  }

  override def start(context: BundleContext): Unit = {
    logger.info("Our bundle started")
  }
}
