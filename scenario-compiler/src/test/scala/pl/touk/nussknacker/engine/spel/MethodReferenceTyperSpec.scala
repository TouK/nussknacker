package pl.touk.nussknacker.engine.spel

import cats.data.{NonEmptyList, Validated}
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.generics.GenericFunctionTypingError.OtherError
import pl.touk.nussknacker.engine.api.generics._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.ArgumentTypeError
import pl.touk.nussknacker.engine.spel.typer.MethodReferenceTyper

class MethodReferenceTyperSpec extends AnyFunSuite with Matchers {

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

  private val methodReferenceTyper = {
    new MethodReferenceTyper(
      ClassDefinitionTestUtils.createDefaultDefinitionForTypesWithExtensions(List(Typed[Helper])),
      methodExecutionForUnknownAllowed = false
    )
  }

  private def extractMethod(name: String, args: List[TypingResult]): Either[ExpressionParseError, TypingResult] = {
    methodReferenceTyper.typeMethodReference(typer.MethodReference(Typed[Helper], isStatic = false, name, args))
  }

  private def checkErrorEquality(a: ArgumentTypeError, b: ArgumentTypeError): Unit = {
    a.name shouldBe b.name
    a.found shouldBe b.found
    a.possibleSignatures.toList should contain theSameElementsAs b.possibleSignatures.toList
  }

  private def checkErrorEquality(a: GenericFunctionTypingError, b: GenericFunctionTypingError): Unit =
    a shouldBe b

  test("should get single method") {
    val name          = "simpleFunction"
    val expectedTypes = List(Typed[Int])

    extractMethod(name, expectedTypes) shouldBe Right(Typed[Int])

    inside(extractMethod(name, List(Typed[String]))) { case Left(error: ArgumentTypeError) =>
      checkErrorEquality(
        error,
        ArgumentTypeError(
          name,
          Signature(List(Typed[String]), None),
          NonEmptyList.one(Signature(expectedTypes, None))
        )
      )
    }
  }

  test("should get overloaded methods") {
    val name           = "simpleOverloadedFunction"
    val expectedTypesA = List(Typed[Int], Typed[Double])
    val expectedTypesB = List(Typed[String], Typed[Double], Typed[String])
    val expectedTypesC = List(Typed[Double], Typed[Long], Typed[Int])

    extractMethod(name, expectedTypesA) shouldBe Right(Typed[String])
    extractMethod(name, expectedTypesB) shouldBe Right(Typed[Int])
    extractMethod(name, expectedTypesC) shouldBe Right(Typed[String])

    inside(extractMethod(name, List())) { case Left(error: ArgumentTypeError) =>
      checkErrorEquality(
        error,
        ArgumentTypeError(
          name,
          Signature(List(), None),
          NonEmptyList.of(
            Signature(expectedTypesA, None),
            Signature(expectedTypesB, None),
            Signature(expectedTypesC, None)
          )
        )
      )
    }
  }

  test("should get single generic method") {
    val name          = "genericFunction"
    val expectedTypes = List(Typed[Int])

    extractMethod(name, expectedTypes) shouldBe Right(Typed[String])

    inside(extractMethod(name, List())) { case Left(error: ArgumentTypeError) =>
      checkErrorEquality(
        error,
        ArgumentTypeError(
          name,
          Signature(List(), None),
          NonEmptyList.one(Signature(expectedTypes, None))
        )
      )
    }
  }

  test("should get overloaded generic method") {
    val name           = "overloadedGenericFunction"
    val expectedTypesA = List(Typed[Double], Typed[Double])
    val expectedTypesB = List(Typed[String], Typed[Int])
    val expectedTypesC = List(Typed[Int], Typed[String], Typed[Float])

    extractMethod("overloadedGenericFunction", expectedTypesA) shouldBe Right(Typed[String])
    extractMethod("overloadedGenericFunction", expectedTypesB) shouldBe Right(Typed[Long])
    extractMethod("overloadedGenericFunction", expectedTypesC) shouldBe Right(Typed[Long])

    inside(extractMethod("overloadedGenericFunction", List())) { case Left(error: ArgumentTypeError) =>
      checkErrorEquality(
        error,
        ArgumentTypeError(
          name,
          Signature(List(), None),
          NonEmptyList.of(
            Signature(expectedTypesA, None),
            Signature(expectedTypesB, None),
            Signature(expectedTypesC, None)
          )
        )
      )
    }
  }

  test("should gen one of overloaded generic methods") {
    val name           = "overloadedMultipleGenericFunction"
    val expectedTypesA = List(Typed[Long])
    val expectedTypesB = List(Typed[String])
    val expectedTypesC = List(Typed[Int], Typed[Double])

    extractMethod(name, expectedTypesA) shouldBe Right(Typed[String])
    extractMethod(name, expectedTypesB) shouldBe Right(Typed[Long])
    extractMethod(name, expectedTypesC) shouldBe Right(Typed[Float])

    inside(extractMethod(name, List())) { case Left(error: ArgumentTypeError) =>
      checkErrorEquality(
        error,
        ArgumentTypeError(
          name,
          Signature(List(), None),
          NonEmptyList.of(
            Signature(expectedTypesA, None),
            Signature(expectedTypesB, None),
            Signature(expectedTypesC, None)
          )
        )
      )
    }
  }

}

trait CustomErrorTypingFunctionHelper extends TypingFunction {
  def expectedArguments: List[TypingResult]
  def result: TypingResult
  def error: String

  override def computeResultType(
      arguments: List[TypingResult]
  ): Validated[NonEmptyList[GenericFunctionTypingError], TypingResult] =
    if (arguments == expectedArguments) result.validNel else OtherError(error).invalidNel

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
