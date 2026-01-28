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

  test("calculates midpoint for symmetric points on equator") {
    val mid = geo.midpoint(0, 0, 0, 2)
    mid.get(0).doubleValue() shouldBe (0.0 +- 1e-10) // lat
    mid.get(1).doubleValue() shouldBe (1.0 +- 1e-10) // lon
  }

  test("calculates midpoint on the same meridian") {
    val mid = geo.midpoint(10, 30, 20, 30)

    mid.get(0).doubleValue() shouldBe (15.0 +- 1e-10) // lat
    mid.get(1).doubleValue() shouldBe (30.0 +- 1e-10) // lon
  }

  test("calculates azimuth on equator eastward") {
    geo.azimuth(0, 0, 0, 10).doubleValue() shouldBe (90.0 +- 1e-9)
  }

  test("calculates azimuth northward") {
    geo.azimuth(0, 0, 10, 0).doubleValue() shouldBe (0.0 +- 1e-9)
  }

  test("calculates azimuth westward on equator") {
    geo.azimuth(0, 0, 0, -10).doubleValue() shouldBe (270.0 +- 1e-9)
  }

  test("closestPointOnLine returns line start when projection is before start") {
    val p = geo.closestPointOnLine(
      0, 0,  // A
      0, 10, // B
      1, -5  // C
    )

    p.get(0).doubleValue() shouldBe (0.0 +- 1e-10)
    p.get(1).doubleValue() shouldBe (0.0 +- 1e-10)
  }

  test("closestPointOnLine returns line end when projection is after end") {
    val p = geo.closestPointOnLine(
      0, 0,  // A
      0, 10, // B
      -2, 20 // C
    )

    p.get(0).doubleValue() shouldBe (0.0 +- 1e-10)
    p.get(1).doubleValue() shouldBe (10.0 +- 1e-10)
  }

  test("closestPointOnLine returns perpendicular projection for point above the segment") {
    val p = geo.closestPointOnLine(
      0, 0,  // A
      0, 10, // B
      5, 7   // C
    )

    p.get(0).doubleValue() shouldBe (0.0 +- 1e-6)
    p.get(1).doubleValue() shouldBe (7.0 +- 1e-6)
  }

  test("closestPointOnLine returns start when segment has zero length") {
    val p = geo.closestPointOnLine(
      10, 20, 10, 20, 30, 40
    )

    p.get(0).doubleValue() shouldBe (10.0 +- 1e-10)
    p.get(1).doubleValue() shouldBe (20.0 +- 1e-10)
  }

}
