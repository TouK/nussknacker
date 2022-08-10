package pl.touk.nussknacker.engine.types

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.generics.{ExpressionParseError, GenericFunctionTypingError, GenericType, Parameter, ParameterList, TypingFunction}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}

import scala.annotation.varargs
import scala.reflect.ClassTag

class GenericFunctionStaticParametersSpec extends FunSuite with Matchers with OptionValues{
  implicit val classExtractionSettings: ClassExtractionSettings = ClassExtractionSettings.Default

  test("should accept valid static parameters") {
    def test(clazz: Class[_]) =
      noException should be thrownBy EspTypeUtils.clazzDefinition(clazz)

    test(classOf[Valid.Foo1])
    test(classOf[Valid.Foo2])
    test(classOf[Valid.Foo4])
  }

  test("should throw exception when trying to declare illegal parameter types") {
    def test(clazz: Class[_]) = {
      intercept[IllegalArgumentException] {
        EspTypeUtils.clazzDefinition(clazz)
      }.getMessage shouldBe "Generic function f has declared parameters that are incompatible with methods signature"
    }

    test(classOf[Invalid.Foo1])
    test(classOf[Invalid.Foo2])
    test(classOf[Invalid.Foo3])
    test(classOf[Invalid.Foo4])
  }
}

private trait IllegalDeclaredParametersHelper extends TypingFunction {
  def params: List[TypingResult]

  def varArgParam: Option[TypingResult]

  override def staticParameters: Option[ParameterList] =
    Some(ParameterList(params.map(Parameter("", _)), varArgParam.map(Parameter("", _))))

  override def computeResultType(arguments: List[TypingResult]): ValidatedNel[GenericFunctionTypingError, TypingResult] =
    Unknown.validNel
}

private object Valid {
  case class Foo1() {
    @GenericType(typingFunction = classOf[Foo1Helper])
    def f(a: Int, b: String): Int = ???
  }

  case class Foo1Helper() extends IllegalDeclaredParametersHelper {
    override def params: List[TypingResult] = List(Typed[Int], Typed[String])

    override def varArgParam: Option[TypingResult] = None
  }


  case class Foo2() {
    @GenericType(typingFunction = classOf[Foo2Helper])
    @varargs
    def f(a: Int, b: String, c: Number*): Int = ???
  }

  case class Foo2Helper() extends IllegalDeclaredParametersHelper {
    override def params: List[TypingResult] = List(Typed[Int], Typed[String], Typed[Double], Typed[Int])

    override def varArgParam: Option[TypingResult] = None
  }


  case class Foo4() {
    @GenericType(typingFunction = classOf[Foo4Helper])
    @varargs
    def f(a: Number, b: String, c: Long*): Int = ???
  }

  case class Foo4Helper() extends IllegalDeclaredParametersHelper {
    override def params: List[TypingResult] = List(Typed[Double], Typed[String], Typed[Long])

    override def varArgParam: Option[TypingResult] = Some(Typed[Long])
  }
}

private object Invalid {
  case class Foo1() {
    @GenericType(typingFunction = classOf[Foo1Helper])
    def f(a: Int, b: String): Int = ???
  }

  case class Foo1Helper() extends IllegalDeclaredParametersHelper {
    override def params: List[TypingResult] = List(Typed[Int])

    override def varArgParam: Option[TypingResult] = None
  }


  case class Foo2() {
    @GenericType(typingFunction = classOf[Foo2Helper])
    @varargs
    def f(a: Int, b: String, c: Number*): Int = ???
  }

  case class Foo2Helper() extends IllegalDeclaredParametersHelper {
    override def params: List[TypingResult] = List(Typed[Int], Typed[String], Typed[Double], Typed[String])

    override def varArgParam: Option[TypingResult] = None
  }


  case class Foo3() {
    @GenericType(typingFunction = classOf[Foo3Helper])
    def f(a: Int, b: String): Int = ???
  }

  case class Foo3Helper() extends IllegalDeclaredParametersHelper {
    override def params: List[TypingResult] = List(Typed[Int])

    override def varArgParam: Option[TypingResult] = Some(Typed[String])
  }


  case class Foo4() {
    @GenericType(typingFunction = classOf[Foo4Helper])
    @varargs
    def f(a: Number, b: String, c: Long*): Int = ???
  }

  case class Foo4Helper() extends IllegalDeclaredParametersHelper {
    override def params: List[TypingResult] = List(Typed[Double], Typed[String], Typed[Double])

    override def varArgParam: Option[TypingResult] = Some(Typed[Long])
  }
}