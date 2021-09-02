package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}

object geo {

  @Documentation(description = "Calculate distance in km between two points (with decimal coordinates), using haversine algorithm")
  def distanceInKm(@ParamName("first point latitude") currentLat: Number, @ParamName("first point longitude") currentLon: Number,
                   @ParamName("second point latitude") otherLat: Number, @ParamName("first point longitude") otherLon: Number): Double =
    distanceInKm(toPoint(currentLat, currentLon), toPoint(otherLat, otherLon))

  @Documentation(description = "Calculate distance in km between two points (with decimal coordinates), using haversine algorithm")
  //https://rosettacode.org/wiki/Haversine_formula#Scala
  def distanceInKm(@ParamName("first point") current: Point, @ParamName("second point") other: Point): Double = {
    val dLat = (current.lat - other.lat).toRadians
    val dLon = (current.lon - other.lon).toRadians

    import scala.math._

    val a = pow(sin(dLat / 2), 2) + pow(sin(dLon / 2), 2) * cos(current.lon.toRadians) * cos(other.lat.toRadians)
    val c = 2 * asin(sqrt(a))
    val R = 6372.8
    c * R
  }

  def toPoint(lat: Number, lon: Number): Point = Point(lat.doubleValue(), lon.doubleValue())

}

case class Point(lat: Double, lon: Double)
