package pl.touk.nussknacker.engine.spel

import cats.data.{NonEmptyList, Validated}
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.Inside.inside
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.generics.{ArgumentTypeError, ExpressionParseError, GenericFunctionError, GenericType, Signature, TypingFunction}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.spel.typer.TypeMethodReference

class TypeMethodReferenceSpec extends FunSuite with Matchers {
  private case class Helper() {
    def simpleFunction(a: Int): Int = ???


    def simpleOverloadedFunction(a: Int, b: Double): String = ???

    def simpleOverloadedFunction(a: String, b: Double, c: String): Int = ???

    def simpleOverloadedFunction(a: Double, b: Long, c: Int): String = ???


    @GenericType(typingFunction = classOf[GenericFunctionHelper])
    def genericFunction(a: Int): String = ???


    @GenericType(typingFunction = classOf[OverloadedGenericFunctionHelper])
    def overloadedGenericFunction(a: Double, b: Double): String = ???

    def overloadedGenericFunction(a: String, b: Int): Long = ???

    def overloadedGenericFunction(a: Int, b: String, c: Float): Long = ???


    @GenericType(typingFunction = classOf[OverloadedMultipleGenericFunctionHelperA])
    def overloadedMultipleGenericFunction(a: Long): String = ???

    @GenericType(typingFunction = classOf[OverloadedMultipleGenericFunctionHelperB])
    def overloadedMultipleGenericFunction(a: String): Long = ???

    @GenericType(typingFunction = classOf[OverloadedMultipleGenericFunctionHelperC])
    def overloadedMultipleGenericFunction(a: Int, b: Double): Float = ???
  }

  private def extractMethod(name: String, args: List[TypingResult]): Either[ExpressionParseError, TypingResult] = {
    TypeMethodReference(name, Typed[Helper], args, isStatic = false, methodExecutionForUnknownAllowed = false)(ClassExtractionSettings.Default)
  }

  private def checkErrorEquality(a: ArgumentTypeError, b: ArgumentTypeError): Unit = {
    a.found.display shouldBe b.found.display
    a.possibleSignatures.map(_.display) should contain theSameElementsAs b.possibleSignatures.map(_.display)
  }

  private def checkErrorEquality(a: GenericFunctionError, b: GenericFunctionError): Unit =
    a.message shouldBe b.message

  test("should get single method") {
    val name = "simpleFunction"
    val expectedTypes = List(Typed[Int])

    extractMethod(name, expectedTypes) shouldBe Right(Typed[Int])

    inside(extractMethod(name, List(Typed[String]))) {
      case Left(error: ArgumentTypeError) => checkErrorEquality(error, new ArgumentTypeError(
        new Signature(name, List(Typed[String]), None),
        List(new Signature(name, expectedTypes, None))
      ))
    }
  }

  test("should get overloaded methods") {
    val name = "simpleOverloadedFunction"
    val expectedTypesA = List(Typed[Int], Typed[Double])
    val expectedTypesB = List(Typed[String], Typed[Double], Typed[String])
    val expectedTypesC = List(Typed[Double], Typed[Long], Typed[Int])

    extractMethod(name, expectedTypesA) shouldBe Right(Typed[String])
    extractMethod(name, expectedTypesB) shouldBe Right(Typed[Int])
    extractMethod(name, expectedTypesC) shouldBe Right(Typed[String])

    inside(extractMethod(name, List())) {
      case Left(error: ArgumentTypeError) => checkErrorEquality(error, new ArgumentTypeError(
        new Signature(name, List(), None),
        List(
          new Signature(name, expectedTypesA, None),
          new Signature(name, expectedTypesB, None),
          new Signature(name, expectedTypesC, None)
        )
      ))
    }
  }

  test("should get single generic method") {
    val name = "genericFunction"
    val expectedTypes = List(Typed[Int])

    extractMethod(name, expectedTypes) shouldBe Right(Typed[String])

    inside(extractMethod(name, List())) {
      case Left(error: GenericFunctionError) => checkErrorEquality(error, new GenericFunctionError("error"))
    }
  }

  test("should get overloaded generic method") {
    val name = "overloadedGenericFunction"
    val expectedTypesA = List(Typed[Double], Typed[Double])
    val expectedTypesB = List(Typed[String], Typed[Int])
    val expectedTypesC = List(Typed[Int], Typed[String], Typed[Float])

    extractMethod("overloadedGenericFunction", expectedTypesA) shouldBe Right(Typed[String])
    extractMethod("overloadedGenericFunction", expectedTypesB) shouldBe Right(Typed[Long])
    extractMethod("overloadedGenericFunction", expectedTypesC) shouldBe Right(Typed[Long])

    inside(extractMethod("overloadedGenericFunction", List())) {
      case Left(error: ArgumentTypeError) => checkErrorEquality(error, new ArgumentTypeError(
        new Signature(name, List(), None),
        new Signature(name, expectedTypesC, None) :: Nil
      ))
    }
  }

  test("should gen one of overloaded generic methods") {
    val name = "overloadedMultipleGenericFunction"
    val expectedTypesA = List(Typed[Long])
    val expectedTypesB = List(Typed[String])
    val expectedTypesC = List(Typed[Int], Typed[Double])

    extractMethod(name, expectedTypesA) shouldBe Right(Typed[String])
    extractMethod(name, expectedTypesB) shouldBe Right(Typed[Long])
    extractMethod(name, expectedTypesC) shouldBe Right(Typed[Float])

    inside(extractMethod(name, List())) {
      case Left(error: GenericFunctionError) => checkErrorEquality(error, new GenericFunctionError("errorC"))
    }
  }
}

trait CustomErrorTypingFunctionHelper extends TypingFunction {
  def expectedArguments: List[TypingResult]
  def result: TypingResult
  def error: String

  override def computeResultType(arguments: List[TypingResult]): Validated[NonEmptyList[ExpressionParseError], TypingResult] =
    if (arguments == expectedArguments) result.validNel else new GenericFunctionError(error).invalidNel
}

trait TypingFunctionHelper extends CustomErrorTypingFunctionHelper {
  override def error: String = "error"
}

case class GenericFunctionHelper() extends TypingFunctionHelper {
  override def expectedArguments: List[TypingResult] = List(Typed[Int])

  override def result: TypingResult = Typed[String]
}

case class OverloadedGenericFunctionHelper() extends TypingFunctionHelper {
  override def expectedArguments: List[TypingResult] = List(Typed[Double], Typed[Double])

  override def result: TypingResult = Typed[String]
}

case class OverloadedMultipleGenericFunctionHelperA() extends CustomErrorTypingFunctionHelper {
  override def expectedArguments: List[TypingResult] = List(Typed[Long])

  override def result: TypingResult = Typed[String]

  override def error: String = "errorA"
}

case class OverloadedMultipleGenericFunctionHelperB() extends CustomErrorTypingFunctionHelper {
  override def expectedArguments: List[TypingResult] = List(Typed[String])

  override def result: TypingResult = Typed[Long]

  override def error: String = "errorB"
}

case class OverloadedMultipleGenericFunctionHelperC() extends CustomErrorTypingFunctionHelper {
  override def expectedArguments: List[TypingResult] = List(Typed[Int], Typed[Double])

  override def result: TypingResult = Typed[Float]

  override def error: String = "errorC"
}