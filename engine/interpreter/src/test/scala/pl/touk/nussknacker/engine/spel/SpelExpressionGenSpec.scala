package pl.touk.nussknacker.engine.spel

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{FunSuite, Inside, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.springframework.expression.spel.support.StandardEvaluationContext
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.{ExpressionParseError, TypedExpression}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedUnion, Unknown}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry

import scala.util.{Failure, Success, Try}

class SpelExpressionGenSpec extends FunSuite with ScalaCheckDrivenPropertyChecks with Matchers with Inside with LazyLogging {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 1000, minSize = 0)

  private val NumberGen: Gen[Number] = Gen.oneOf(
    Arbitrary.arbitrary[Byte].map(n => n: java.lang.Byte),
    Arbitrary.arbitrary[Short].map(n => n: java.lang.Short),
    Arbitrary.arbitrary[Int].map(n => n: java.lang.Integer),
    Arbitrary.arbitrary[Long].map(n => n: java.lang.Long),
    Arbitrary.arbitrary[BigInt].map(_.bigInteger),
    Arbitrary.arbitrary[Float].map(n => n: java.lang.Float),
    Arbitrary.arbitrary[Double].map(n => n: java.lang.Double),
    Arbitrary.arbitrary[BigDecimal].map(_.bigDecimal))

  private val NonZeroNumberGen = NumberGen.filterNot(_.doubleValue() == 0)

  private val NoRestrictionsOperarators = List("+", "-", "*")

  private val NotAcceptingZeroOnRrightOperarators = List("/", "%")

  test("all combinations of simple arithmetic operators and operands") {
    val operatorGen = Gen.oneOf(NoRestrictionsOperarators)

    forAll(NumberGen, NumberGen, operatorGen) { (a, b, op) =>
      checkIfEvaluatedClassMatchesExpected(op, a, b)
    }
  }

  test("all combinations of operands for power operator") {
    forAll(NumberGen, NumberGen) { (a, b) =>
      (a, b) match {
        case (_: java.math.BigInteger | _: java.math.BigDecimal, bNum: Number) if bNum.doubleValue <= 0 || bNum.doubleValue > 1000 =>
          // BigInteger and BigDecimal not accept non positive exponent and has complexity dependent on exponent value
        case _ =>
          checkIfEvaluatedClassMatchesExpected("^", a, b)
      }
    }
  }

  test("all combinations of operands for divide and modulus operator") {
    val operatorGen = Gen.oneOf(NotAcceptingZeroOnRrightOperarators)

    forAll(NumberGen, NonZeroNumberGen, operatorGen) { (a, b, op) =>
      checkIfEvaluatedClassMatchesExpected(op, a, b)
    }
  }

  private def checkIfEvaluatedClassMatchesExpected(op: String, a: Any, b: Any) = {
    val expr = s"#a $op #b"
    val debugMessage = s"expression: $expr; operands: $a, $b; operand types: ${a.getClass}, ${b.getClass}"
    logger.debug(debugMessage)

    withClue(debugMessage) {
      Try(evaluate(expr, a, b)) match {
        case Failure(a: ArithmeticException) => // in case of overflow or similar exception, typed Type doesn't need to match
          logger.debug(s"Ignored arithmetic exception: ${a.getMessage}")
        case Failure(other) =>
          fail(other) // shouldn't happen
        case Success(evaluatedClass) =>
          inside(validate(expr, a, b)) {
            case Valid(TypedExpression(_, TypedClass(typedClass, Nil), _)) =>
              typedClass shouldEqual evaluatedClass
            case Valid(TypedExpression(_, TypedUnion(possibleTypes), _)) =>
              val typedClasses = possibleTypes.map(_.asInstanceOf[TypedClass].klass)
              typedClasses should contain(evaluatedClass)
          }
      }
    }
  }

  private def evaluate(expr: String, a: Any, b: Any): Class[_] = {
    val spelParser = new org.springframework.expression.spel.standard.SpelExpressionParser()
    val evaluationContext = new StandardEvaluationContext()
    evaluationContext.setVariable("a", a)
    evaluationContext.setVariable("b", b)
    spelParser.parseRaw(expr).getValue(evaluationContext).getClass
  }

  private def validate(expr: String, a: Any, b: Any): ValidatedNel[ExpressionParseError, TypedExpression] = {
    val parser = SpelExpressionParser.default(getClass.getClassLoader, new SimpleDictRegistry(Map.empty), enableSpelForceCompile = false, strictTypeChecking = true,
      List.empty, SpelExpressionParser.Standard, strictMethodsChecking = true, staticMethodInvocationsChecking = false, TypeDefinitionSet.empty,
      disableMethodExecutionForUnknown = false)(ClassExtractionSettings.Default)
    implicit val nodeId: NodeId = NodeId("fooNode")
    val validationContext = ValidationContext.empty
      .withVariable("a", Typed.fromInstance(a), paramName = None).toOption.get
      .withVariable("b", Typed.fromInstance(b), paramName = None).toOption.get
    parser.parse(expr, validationContext, Unknown)
  }

}
