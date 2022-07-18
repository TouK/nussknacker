package pl.touk.nussknacker.engine.util.functions

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.function.{ExtendedFunction, Parameter, Signature}
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

  @Documentation(description = "myFunction is a generic function")
  @GenericType(typingFunction = classOf[MyFunctionHelper])
  def myFunction(arguments: List[Any]): Any = arguments match {
    case (_: Int) :: Nil => "OK: Int"
    case (_: String) :: Nil => "OK: String"
    case _ => throw new AssertionError("method called with argument that should cause validation error")
  }
}

case class Point(lat: Double, lon: Double)

private class MyFunctionHelper extends TypingFunction {
  private val intParameter = Parameter(Typed[Int], "arg Int", Some("this is Int argument"))
  private val stringParameter = Parameter(Typed[String], "arg String", Some("this is String argument"))
  private val intSignature = Signature(List(intParameter), None, Typed.fromInstance("Int"), Some("this checks for int argument"))
  private val stringSignature = Signature(List(stringParameter), None, Typed.fromInstance("String"), Some("this checks for string argument"))

  def signatures: Some[NonEmptyList[Signature]] = Some(NonEmptyList(intSignature, List(stringSignature)))

  override def apply(arguments: List[TypingResult]): ValidatedNel[String, TypingResult] = arguments match {
    case x :: Nil if x.canBeSubclassOf(Typed[Int]) => Typed.fromInstance("OK: Int").validNel
    case x :: Nil if x.canBeSubclassOf(Typed[String]) => Typed.fromInstance("OK: String").validNel
    case _ => "Error message".invalidNel
  }
}