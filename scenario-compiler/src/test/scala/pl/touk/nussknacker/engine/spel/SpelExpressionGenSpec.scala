package pl.touk.nussknacker.engine.spel

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.springframework.expression.spel.support.StandardEvaluationContext
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.parse.TypedExpression
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder

import scala.util.{Failure, Success, Try}

class SpelExpressionGenSpec
    extends AnyFunSuite
    with ScalaCheckDrivenPropertyChecks
    with Matchers
    with Inside
    with LazyLogging {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 1000, minSize = 0)

  private val NumberGen: Gen[Number] = Gen.oneOf(
    Arbitrary.arbitrary[Byte].map(n => n: java.lang.Byte),
    Arbitrary.arbitrary[Short].map(n => n: java.lang.Short),
    Arbitrary.arbitrary[Int].map(n => n: java.lang.Integer),
    Arbitrary.arbitrary[Long].map(n => n: java.lang.Long),
    Arbitrary.arbitrary[BigInt].map(_.bigInteger),
    Arbitrary.arbitrary[Float].map(n => n: java.lang.Float),
    Arbitrary.arbitrary[Double].map(n => n: java.lang.Double),
    Arbitrary.arbitrary[BigDecimal].map(_.bigDecimal)
  )

  private val NonZeroNumberGen = NumberGen.filterNot(_.doubleValue() == 0)

  private val NoRestrictionsOperators = List("+", "-", "*")

  private val NotAcceptingZeroOnRightOperators = List("/", "%")

  test("all combinations of simple arithmetic operators and operands") {
    val operatorGen = Gen.oneOf(NoRestrictionsOperators)

    forAll(NumberGen, NumberGen, operatorGen) { (a, b, op) =>
      checkIfEvaluatedClassMatchesExpected(op, a, b)
    }
  }

  test("all combinations of operands for power operator") {
    forAll(NumberGen, NumberGen) { (a, b) =>
      (a, b) match {
        case (_: java.math.BigInteger | _: java.math.BigDecimal, bNum: Number)
            if bNum.doubleValue <= 0 || bNum.doubleValue > 1000 =>
        // BigInteger and BigDecimal not accept non positive exponent and has complexity dependent on exponent value
        case _ =>
          checkIfEvaluatedClassMatchesExpected("^", a, b)
      }
    }
  }

  test("special combination of operands for power operator") {
    checkIfEvaluatedClassMatchesExpected("^", Int.MaxValue, Int.MaxValue)
  }

  test("all combinations of operands for divide and modulus operator") {
    val operatorGen = Gen.oneOf(NotAcceptingZeroOnRightOperators)

    forAll(NumberGen, NonZeroNumberGen, operatorGen) { (a, b, op) =>
      checkIfEvaluatedClassMatchesExpected(op, a, b)
    }
  }

  private def checkIfEvaluatedClassMatchesExpected(op: String, a: Number, b: Number): Unit = {
    val expr         = s"#a $op #b"
    val debugMessage = s"expression: $expr; operands: $a, $b; operand types: ${a.getClass} and ${b.getClass}."
    logger.debug(debugMessage)

    withClue(debugMessage) {
      Try(evaluate(expr, a, b)) match {
        // in case of overflow or similar exception, typed Type doesn't need to match
        case Failure(a: ArithmeticException) =>
          logger.debug(s"Ignored arithmetic exception: ${a.getMessage}")
        case Failure(other) =>
          fail(other) // shouldn't happen
        case Success(evaluatedValue) =>
          inside(validate(expr, a, b)) { case Valid(typedExpression) =>
            typedExpression.typingInfo.typingResult match {
              case TypedObjectWithValue(TypedClass(typedClass, Nil, _), typedValue) =>
                typedValue shouldEqual evaluatedValue
                typedClass shouldEqual evaluatedValue.getClass
              case TypedClass(typedClass, Nil, _) =>
                typedClass shouldEqual evaluatedValue.getClass
              case union: TypedUnion =>
                val typedClasses = union.possibleTypes.map(_.asInstanceOf[TypedClass].klass)
                typedClasses.toList should contain(evaluatedValue.getClass)
              case other =>
                throw new IllegalArgumentException(s"Not expected typing result: $other")
            }
          }
      }
    }
  }

  private def evaluate(expr: String, a: Any, b: Any): AnyRef = {
    val spelParser        = new org.springframework.expression.spel.standard.SpelExpressionParser()
    val evaluationContext = new StandardEvaluationContext()
    evaluationContext.setVariable("a", a)
    evaluationContext.setVariable("b", b)
    spelParser.parseRaw(expr).getValue(evaluationContext)
  }

  private def validate(expr: String, a: Any, b: Any): ValidatedNel[ExpressionParseError, TypedExpression] = {
    val parser = SpelExpressionParser.default(
      getClass.getClassLoader,
      ModelDefinitionBuilder.emptyExpressionConfig,
      new SimpleDictRegistry(Map.empty),
      enableSpelForceCompile = false,
      SpelExpressionParser.Standard,
      ClassDefinitionTestUtils.createDefinitionForDefaultAdditionalClasses
    )
    implicit val nodeId: NodeId = NodeId("fooNode")
    val validationContext = ValidationContext.empty
      .withVariable("a", Typed.fromInstance(a), paramName = None)
      .toOption
      .get
      .withVariable("b", Typed.fromInstance(b), paramName = None)
      .toOption
      .get
    parser.parse(expr, validationContext, Unknown)
  }

}
