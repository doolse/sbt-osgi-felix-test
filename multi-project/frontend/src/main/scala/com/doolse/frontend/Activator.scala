package com.doolse.frontend

import com.doolse.core.CoreService
import org.osgi.framework.{BundleContext, BundleActivator}

/**
 * Created by jolz on 9/09/15.
 */
class Activator extends BundleActivator {
  override def stop(context: BundleContext): Unit = { }

  override def start(context: BundleContext): Unit = {
    CoreService.isEverythingOk
  }
}
