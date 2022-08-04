package pl.touk.nussknacker.engine.types

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.generics.{ExpressionParseError, GenericType, TypingFunction}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}

class EspTypeUtilsGenericFunctionValidation extends FunSuite with Matchers with OptionValues{
  implicit val classExtractionSettings: ClassExtractionSettings = ClassExtractionSettings.Default

  test("should throw exception when trying to declare illegal parameter types") {
    intercept[IllegalArgumentException] {
      EspTypeUtils.clazzDefinition(classOf[Foo1])
    }.getMessage shouldBe "Generic function f has declared parameters that are incompatible with methods signature"
  }
}

private trait IllegalDeclaredParametersHelper extends TypingFunction {
  def parameterTypes: List[TypingResult]

  override def staticParameters(): Option[List[(String, TypingResult)]] = Some(parameterTypes.map(("", _)))

  override def staticVarArgParamete

  override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] =
    Unknown.validNel
}


private case class Foo1() {
  @GenericType(typingFunction = classOf[Foo1Helper])
  def f(a: Int, b: String): Int = ???
}

private case class Foo1Helper() extends IllegalDeclaredParametersHelper {
  override def parameterTypes: List[TypingResult] = List(Typed[Int])
}


private case class Foo2() {
  @GenericType(typingFunction = classOf[Foo2Helper])
  def f(a: Int, b: String): Int = ???
}

private case class Foo2Helper() extends IllegalDeclaredParametersHelper {
  override def parameterTypes: List[TypingResult] = List(Typed[Int], Typed[String], Typed[Double])
}


private case class Foo3() {
  @GenericType(typingFunction = classOf[Foo3Helper])
  def f(a: Int, b: String): Int = ???
}

private case class Foo3Helper() extends IllegalDeclaredParametersHelper {
  override def parameterTypes: List[TypingResult] = List(Typed[Int])


}


private case class Foo4() {
  @GenericType(typingFunction = classOf[Foo4Helper])
  def f(a: Int, b: String): Int = ???
}

private case class Foo4Helper() extends IllegalDeclaredParametersHelper {
  override def parameterTypes: List[TypingResult] = List(Typed[Int])
}