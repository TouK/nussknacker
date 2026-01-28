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

  @Documentation(description =
    "Calculate geographic midpoint between two points on Earth. Returns [latitude, longitude]."
  )
  def midpoint(
      @ParamName("latitude1") latitude1: Number,
      @ParamName("longitude1") longitude1: Number,
      @ParamName("latitude2") latitude2: Number,
      @ParamName("longitude2") longitude2: Number
  ): java.util.List[java.lang.Double] = {
    import scala.math._

    val lat1 = latitude1.doubleValue().toRadians
    val lon1 = longitude1.doubleValue().toRadians
    val lat2 = latitude2.doubleValue().toRadians
    val lon2 = longitude2.doubleValue().toRadians

    val dLon = lon2 - lon1

    val bx = cos(lat2) * cos(dLon)
    val by = cos(lat2) * sin(dLon)

    val lat3 = atan2(
      sin(lat1) + sin(lat2),
      sqrt(pow(cos(lat1) + bx, 2) + by * by)
    )
    val lon3 = lon1 + atan2(by, cos(lat1) + bx)

    java.util.Arrays.asList(
      lat3.toDegrees,
      lon3.toDegrees
    )
  }

  @Documentation(description =
    "Calculate initial bearing (azimuth) in degrees from the first point to the second point. Result in range [0, 360)."
  )
  def azimuth(
      @ParamName("latitude1") latitude1: Number,
      @ParamName("longitude1") longitude1: Number,
      @ParamName("latitude2") latitude2: Number,
      @ParamName("longitude2") longitude2: Number
  ): Double = {
    import scala.math._

    val lat1 = latitude1.doubleValue().toRadians
    val lat2 = latitude2.doubleValue().toRadians
    val dLon = (longitude2.doubleValue() - longitude1.doubleValue()).toRadians

    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) -
      sin(lat1) * cos(lat2) * cos(dLon)

    val bearing = atan2(y, x).toDegrees
    (bearing + 360.0) % 360.0
  }

  @Documentation(description =
    "Calculate the closest point on the line segment AB to point C. Returns [latitude, longitude]."
  )
  def closestPointOnLine(
      @ParamName("lineStartLat") lineStartLat: Number,
      @ParamName("lineStartLon") lineStartLon: Number,
      @ParamName("lineEndLat") lineEndLat: Number,
      @ParamName("lineEndLon") lineEndLon: Number,
      @ParamName("pointLat") pointLat: Number,
      @ParamName("pointLon") pointLon: Number
  ): java.util.List[java.lang.Double] = {
    import scala.math._

    val refLat    = (lineStartLat.doubleValue() + lineEndLat.doubleValue()) / 2.0
    val cosRefLat = cos(refLat.toRadians)

    def project(lat: Double, lon: Double): (Double, Double) = {
      val x = lon.toRadians * cosRefLat
      val y = lat.toRadians
      (x, y)
    }

    val (ax, ay) = project(lineStartLat.doubleValue(), lineStartLon.doubleValue())
    val (bx, by) = project(lineEndLat.doubleValue(), lineEndLon.doubleValue())
    val (cx, cy) = project(pointLat.doubleValue(), pointLon.doubleValue())

    val abx = bx - ax
    val aby = by - ay
    val acx = cx - ax
    val acy = cy - ay

    val abLenSq = abx * abx + aby * aby
    val t =
      if (abLenSq == 0) 0.0
      else (acx * abx + acy * aby) / abLenSq

    val clampedT = max(0.0, min(1.0, t))

    val dx = ax + clampedT * abx
    val dy = ay + clampedT * aby

    val lat = dy.toDegrees
    val lon = (dx / cosRefLat).toDegrees

    java.util.Arrays.asList(lat, lon)
  }

}

private object GeoUtils {
  // 6371 is recommended by the International Union of Geodesy and Geophysics,
  // it minimizes the RMS relative error between the great circle and geodesic distance
  private val EarthMeanRadius: Long = 6371
}
