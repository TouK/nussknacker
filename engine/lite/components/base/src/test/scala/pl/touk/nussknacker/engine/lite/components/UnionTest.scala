package pl.touk.nussknacker.engine.lite.components

import cats.Monad
import cats.data.ValidatedNel
import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.compile.{CompilationResult, ProcessValidator}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSource
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import scala.concurrent.Future
import scala.language.higherKinds

class UnionTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  test("unification of same types") {
    val validationResult = validate("123", "234")
    validationResult.typing("end").inputValidationContext("unified") shouldEqual Typed[Integer]
    validationResult.result.toEither.rightValue
  }

  test("unification of types with common supertype") {
    val validationResult = validate("123", "234.56")
    validationResult.typing("end").inputValidationContext("unified") shouldEqual Typed[Number]
    validationResult.result.toEither.rightValue
  }

  test("unification of different types") {
    val validationResult = validate("123", "'foo'")
    validationResult.result.toEither.leftValue.toList should contain(
      CannotCreateObjectError("All branch values must be of the same type", "union")
    )
  }

  test("unification of map types with common supertype") {
    validate("{a: 123}", "{a: 234.56}").result.toEither.rightValue
    validate("{a: 123}", "{a: 'string'}").result.toEither.leftValue
    validate("{a: 123}", "{b: 234.56}").result.toEither.leftValue
  }

  private def validate(leftValueExpression: String, rightValueExpression: String): CompilationResult[Unit] = {
    val scenario = ScenarioBuilder
      .streamingLite("test")
      .sources(
        GraphBuilder
          .source("left-source", "typed-source", "value" -> leftValueExpression)
          .branchEnd("left-source", "union"),
        GraphBuilder
          .source("right-source", "typed-source", "value" -> rightValueExpression)
          .branchEnd("right-source", "union"),
        GraphBuilder
          .join(
            "union",
            "union",
            Some("unified"),
            List(
              "left-source" -> List(
                "Output expression" -> "#input"
              ),
              "right-source" -> List(
                "Output expression" -> "#input"
              )
            )
          )
          .processorEnd("end", "dumb")
      )

    val modelData = LocalModelData(
      ConfigFactory.empty(),
      new EmptyProcessConfigCreator,
      List(
        ComponentDefinition("typed-source", TypedSourceFactory),
        ComponentDefinition("dumb", DumbService)
      )
    )
    val validator        = ProcessValidator.default(modelData)
    val validationResult = validator.validate(scenario)
    validationResult
  }

}

object TypedSourceFactory extends SourceFactory {

  @MethodToInvoke
  def invoke(@ParamName("value") value: LazyParameter[AnyRef]): Source =
    new LiteSource[Any] with ReturningType {

      override def createTransformation[F[_]: Monad](
          evaluateLazyParameter: customComponentTypes.CustomComponentContext[F]
      ): Any => ValidatedNel[ErrorType, Context] = ???

      override def returnType: typing.TypingResult = value.returnType
    }

}

object DumbService extends Service {
  @MethodToInvoke
  def invoke(): Future[Any] = ???
}
