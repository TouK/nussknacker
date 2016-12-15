package pl.touk.esp.ui.app

import argonaut.PrettyParams
import com.typesafe.scalalogging.LazyLogging

class BuildInfoHolder(processesBuildInfo: Map[String, String]) extends LazyLogging {
  import argonaut.Argonaut._

  private val buildInfo: Map[String, String] = {
    scala.collection.immutable.TreeMap(processesBuildInfo.toList: _*)
  }

  val buildInfoAsJson = {
    val prettyParams = PrettyParams.spaces2.copy(preserveOrder = true)
    buildInfo.asJson.pretty(prettyParams)
  }
}