package com.test.sbt

/**
 * Created by jolz on 12/08/15.
 */
import scalaz.{-\/, \/}
import argonaut._

class Ok extends App {

  implicit def blah = EncodeJson[String](_ => ???)
  def fudge: String \/ Int = -\/("sdasd")
}
