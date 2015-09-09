package com.doolse.core

import org.slf4j.LoggerFactory

/**
 * Created by jolz on 9/09/15.
 */
object CoreService {

  val Logger = LoggerFactory.getLogger(getClass)

  def isEverythingOk : Boolean = {
    Logger.info("Everything appears to be ok")
    true
  }

}
