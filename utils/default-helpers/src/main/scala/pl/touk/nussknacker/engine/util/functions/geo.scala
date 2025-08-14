package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}

object geo extends GeoUtils

trait GeoUtils extends HideToString {

  import GeoUtils._

  @Documentation(description =
    "Calculate distance in kilometers between two points (with decimal coordinates), using the Haversine formula"
  )
  def distanceInKm(
      @ParamName("latitude1") latitude1: Number,
      @ParamName("longitude1") longitude1: Number,
      @ParamName("latitude2") latitude2: Number,
      @ParamName("longitude2") longitude2: Number
  ): Double = {
    // https://rosettacode.org/wiki/Haversine_formula#Scala
    import scala.math._

    val dLat = (latitude1.doubleValue() - latitude2.doubleValue()).toRadians
    val dLon = (longitude1.doubleValue() - longitude2.doubleValue()).toRadians

    val a = pow(sin(dLat / 2), 2) + pow(sin(dLon / 2), 2) * cos(latitude1.doubleValue().toRadians) * cos(
      latitude2.doubleValue().toRadians
    )
    val c = 2 * asin(sqrt(a))
    c * EarthMeanRadius
  }

}

private object GeoUtils {
  // 6371 is recommended by the International Union of Geodesy and Geophysics,
  // it minimizes the RMS relative error between the great circle and geodesic distance
  private val EarthMeanRadius: Long = 6371
}
