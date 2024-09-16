package pl.touk.nussknacker.engine.definition.clazz

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.generics._
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}

import scala.annotation.varargs

class GenericFunctionStaticParametersSpec extends AnyFunSuite with Matchers with OptionValues {
  private val classDefinitionExtractor = ClassDefinitionExtractor.Default

  test("should accept valid static parameters") {
    def test(clazz: Class[_]) =
      noException should be thrownBy classDefinitionExtractor.extract(clazz)

    test(classOf[Valid.Foo1])
    test(classOf[Valid.Foo2])
    test(classOf[Valid.Foo4])
  }

  test("should throw exception when trying to declare illegal parameter types") {
    def test(clazz: Class[_], message: String) = {
      intercept[IllegalArgumentException] {
        classDefinitionExtractor.extract(clazz)
      }.getMessage shouldBe message
    }

    test(
      classOf[Invalid.Foo1],
      "Generic function f has declared parameters that are incompatible with methods signature: wrong number of no-vararg arguments: found 1, expected: 2"
    )
    test(
      classOf[Invalid.Foo2],
      "Generic function f has declared parameters that are incompatible with methods signature: argument at position 4 has illegal type: String cannot be subclass of Number"
    )
    test(
      classOf[Invalid.Foo3],
      "Generic function f has declared parameters that are incompatible with methods signature: function with varargs cannot be more specific than function without varargs; wrong number of no-vararg arguments: found 1, expected: 2"
    )
    test(
      classOf[Invalid.Foo4],
      "Generic function f has declared parameters that are incompatible with methods signature: argument at position 3 has illegal type: Double cannot be subclass of Long"
    )
  }

}

private trait TypingFunctionHelper extends TypingFunction {
  def params: List[TypingResult]

  def varArgParam: Option[TypingResult]

  override def signatures: Option[NonEmptyList[MethodTypeInfo]] =
    Some(NonEmptyList.one(MethodTypeInfo(params.map(Parameter("", _)), varArgParam.map(Parameter("", _)), Unknown)))

  override def computeResultType(
      arguments: List[TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, TypingResult] =
    Unknown.validNel

}

private object Valid {

  class Foo1 {
    @GenericType(typingFunction = classOf[Foo1TypingFunction])
    def f(a: Int, b: String): Int = ???
  }

  class Foo1TypingFunction extends TypingFunctionHelper {
    override def params: List[TypingResult] = List(Typed[Int], Typed[String])

    override def varArgParam: Option[TypingResult] = None
  }

  class Foo2 {

    @GenericType(typingFunction = classOf[Foo2TypingFunction])
    @varargs
    def f(a: Int, b: String, c: Number*): Int = ???

  }

  class Foo2TypingFunction extends TypingFunctionHelper {
    override def params: List[TypingResult] = List(Typed[Int], Typed[String], Typed[Double], Typed[Int])

    override def varArgParam: Option[TypingResult] = None
  }

  class Foo4 {

    @GenericType(typingFunction = classOf[Foo4TypingFunction])
    @varargs
    def f(a: Number, b: String, c: Long*): Int = ???

  }

  class Foo4TypingFunction extends TypingFunctionHelper {
    override def params: List[TypingResult] = List(Typed[Double], Typed[String], Typed[Long])

    override def varArgParam: Option[TypingResult] = Some(Typed[Long])
  }

}

private object Invalid {

  class Foo1 {
    @GenericType(typingFunction = classOf[Foo1TypingFunction])
    def f(a: Int, b: String): Int = ???
  }

  class Foo1TypingFunction extends TypingFunctionHelper {
    override def params: List[TypingResult] = List(Typed[Int])

    override def varArgParam: Option[TypingResult] = None
  }

  class Foo2 {

    @GenericType(typingFunction = classOf[Foo2TypingFunction])
    @varargs
    def f(a: Int, b: String, c: Number*): Int = ???

  }

  class Foo2TypingFunction extends TypingFunctionHelper {
    override def params: List[TypingResult] = List(Typed[Int], Typed[String], Typed[Double], Typed[String])

    override def varArgParam: Option[TypingResult] = None
  }

  class Foo3 {
    @GenericType(typingFunction = classOf[Foo3TypingFunction])
    def f(a: Int, b: String): Int = ???
  }

  class Foo3TypingFunction extends TypingFunctionHelper {
    override def params: List[TypingResult] = List(Typed[Int])

    override def varArgParam: Option[TypingResult] = Some(Typed[String])
  }

  class Foo4 {

    @GenericType(typingFunction = classOf[Foo4TypingFunction])
    @varargs
    def f(a: Number, b: String, c: Long*): Int = ???

  }

  class Foo4TypingFunction extends TypingFunctionHelper {
    override def params: List[TypingResult] = List(Typed[Double], Typed[String], Typed[Double])

    override def varArgParam: Option[TypingResult] = Some(Typed[Long])
  }

}
