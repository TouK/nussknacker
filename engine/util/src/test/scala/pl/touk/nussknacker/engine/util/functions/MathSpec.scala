package pl.touk.nussknacker.engine.util.functions

import org.scalatest.{FunSuite, Matchers}

class MathSpec extends FunSuite with Matchers {

  test("should return max") {
    math.max(2.0d, 2.1d) shouldEqual 2.1d
    math.max(2.0f, 2.1f) shouldEqual 2.1f
    math.max(2.0f, 2.1d) shouldEqual 2.1d
    math.max(2L, 1L) shouldEqual 2L
    math.max(2, 1) shouldEqual 2
    math.max(2, 1d) shouldEqual 2
    math.max(2, 1f) shouldEqual 2
  }

  test("should return min") {
    math.min(2.0d, 2.1d) shouldEqual 2.0d
    math.min(2.0f, 2.1f) shouldEqual 2.0f
    math.min(2.0f, 2.1d) shouldEqual 2.0d
    math.min(2L, 1L) shouldEqual 1L
    math.min(2, 1) shouldEqual 1
    math.min(2, 1d) shouldEqual 1
    math.min(2, 1d) shouldEqual 1
  }

  test("should return abs") {
    math.abs(-2.0d) shouldEqual 2
    math.abs(-2.0f) shouldEqual 2
    math.abs(-2L) shouldEqual 2
    math.abs(-2) shouldEqual 2
  }

  test("should return floor") {
    math.floor(-2.1d) shouldEqual -3.0
    math.floor(2.1d) shouldEqual 2
  }
}
