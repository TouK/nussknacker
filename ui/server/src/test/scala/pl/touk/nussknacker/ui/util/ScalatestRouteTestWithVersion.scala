package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.Suite

trait ScalatestRouteTestWithVersion extends ScalatestRouteTest { this: Suite =>

  /*val scalaBinaryVersion: String = util.Properties.versionNumberString.replaceAll("(\\d+\\.\\d+)\\..*$", "$1")

  override def testConfigSource: String = {
    System.setProperty("scala.binary.version", util.Properties.versionNumberString.replaceAll("(\\d+\\.\\d+)\\..*$", "$1"))
    s"""{scala.binary.version = $scalaBinaryVersion}"""
  } */
}
