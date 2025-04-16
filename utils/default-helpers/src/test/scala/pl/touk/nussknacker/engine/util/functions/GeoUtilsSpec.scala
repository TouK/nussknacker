package pl.touk.nussknacker.engine.util.functions

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GeoUtilsSpec extends AnyFunSuite with Matchers {

  test("accepts Null Island") {
    geo.distanceInKm(0, 0, 0, 0) shouldBe 0
  }

  test("calculates distance between the White House and the Eiffel Tower") {
    val whiteHouse  = (38.898, -77.037)
    val eiffelTower = (48.858, 2.294)

    val distanceOnWGS84Ellipsoid = 6177.45
    // we assume up to 0.5% error
    geo.distanceInKm(whiteHouse._1, whiteHouse._2, eiffelTower._1, eiffelTower._2) should ===(
      distanceOnWGS84Ellipsoid +- distanceOnWGS84Ellipsoid * 0.005
    )
  }

}
