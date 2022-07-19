package pl.touk.nussknacker.engine.util.functions

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.{Documentation, GenericType, ParamName, TypingFunction}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}


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

  private class MyFunctionHelper extends TypingFunction {
    private val IntOK = "OK: Int"
    private val StringOK = "OK: String"

    override def expectedParameters(): List[(String, TypingResult)] =
      List(("example of desired type", Typed(Typed[Int], Typed[String])))

    override def expectedResult(): TypingResult =
      Typed(Typed.fromInstance(IntOK), Typed.fromInstance(StringOK))

    override def apply(arguments: List[TypingResult]): ValidatedNel[String, TypingResult] = arguments match {
      case x :: Nil if x.canBeSubclassOf(Typed[Int]) => Typed.fromInstance(IntOK).validNel
      case x :: Nil if x.canBeSubclassOf(Typed[String]) => Typed.fromInstance(StringOK).validNel
      case _ => "Error message".invalidNel
    }

    def applyValue(arguments: List[Any]): Any = arguments match {
      case (_: Int) :: Nil =>
      case (_: String) :: Nil => "OK: String"
      case _ => throw new AssertionError("method called with argument that should cause validation error")
    }
  }

  @Documentation(description = "myFunction is a generic function")
  @GenericType(typingFunction = classOf[MyFunctionHelper])
  def myFunction(arguments: List[Any]): Any = (new MyFunctionHelper).applyValue(arguments)
}

case class Point(lat: Double, lon: Double)
