package pl.touk.nussknacker.ui.app

import io.circe.{Decoder, Printer}
import io.circe.syntax._
import io.circe.parser._

object BuildInfo {

  val empty = Map.empty[String, String]

  def writeAsJson(buildInfo: Map[String, String]): String = {
    val prettyParams = Printer.spaces2.copy(sortKeys = true)
    ordered(buildInfo).asJson.printWith(prettyParams)
  }

  def parseJson(json: String): Option[Map[String, String]] = {
    parse(json).right.toOption
      .flatMap(js => Decoder[Map[String, String]].decodeJson(js).right.toOption).map(ordered)
  }

  def ordered(map: Map[String, String]): Map[String, String] = {
    scala.collection.immutable.TreeMap(map.toList: _*)
  }

}