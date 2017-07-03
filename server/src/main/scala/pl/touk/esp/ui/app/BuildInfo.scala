package pl.touk.esp.ui.app

import argonaut.{Parse, PrettyParams}

object BuildInfo {
  import argonaut.Argonaut._

  val empty = Map.empty[String, String]

  def writeAsJson(buildInfo: Map[String, String]) = {
    val prettyParams = PrettyParams.spaces2.copy(preserveOrder = true)
    ordered(buildInfo).asJson.pretty(prettyParams)
  }

  def parseJson(json: String): Option[Map[String, String]] = {
    Parse.decodeOption[Map[String, String]](json).map(ordered)
  }

  def ordered(map: Map[String, String]): Map[String, String] = {
    scala.collection.immutable.TreeMap(map.toList: _*)
  }

}