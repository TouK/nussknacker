package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}

object geo extends GeoUtils

trait GeoUtils extends HideToString {

  import GeoUtils._

  @Documentation(description =
    "Calculate distance in kilometers between two points (with decimal coordinates), using the Haversine formula"
  )
  def distanceInKm(
      @ParamName("First point - latitude") lat1: Number,
      @ParamName("First point - longitude") lon1: Number,
      @ParamName("Second point - latitude") lat2: Number,
      @ParamName("Second point - longitude") lon2: Number
  ): Double = {
    // https://rosettacode.org/wiki/Haversine_formula#Scala
    import scala.math._

    val dLat = (lat1.doubleValue() - lat2.doubleValue()).toRadians
    val dLon = (lon1.doubleValue() - lon2.doubleValue()).toRadians

    val a = pow(sin(dLat / 2), 2) + pow(sin(dLon / 2), 2) * cos(lat1.doubleValue().toRadians) * cos(
      lat2.doubleValue().toRadians
    )
    val c = 2 * asin(sqrt(a))
    c * EARTH_MEAN_RADIUS
  }

}

object GeoUtils {
  // 6371 is recommended by the International Union of Geodesy and Geophysics,
  // it minimizes the RMS relative error between the great circle and geodesic distance
  val EARTH_MEAN_RADIUS: Long = 6371
}
