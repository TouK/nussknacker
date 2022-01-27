package pl.touk.nussknacker.engine.lite.components

import cats.Monad
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LiteStreamMetaData, MetaData, MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.compile.{CompilationResult, ProcessValidator}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSource
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import scala.concurrent.Future
import scala.language.higherKinds

class UnionTest extends FunSuite with Matchers with EitherValuesDetailedMessage {

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
    validationResult.result.toEither.leftValue.toList should contain (CannotCreateObjectError("All branch values must be of the same type", "union"))
  }

  private def validate(leftValueExpression: String, rightValueExpression: String): CompilationResult[Unit] = {
    val scenario = EspProcess(MetaData("test", LiteStreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder
        .source("left-source", "typed-source", "value" -> leftValueExpression)
        .branchEnd("left-source", "union"),
      GraphBuilder
        .source("right-source", "typed-source", "value" -> rightValueExpression)
        .branchEnd("right-source", "union"),
      GraphBuilder
        .branch("union", "union", Some("unified"), List(
          "left-source" -> List(
            "Output expression" -> "#input"
          ),
          "right-source" -> List(
            "Output expression" -> "#input"
          )
        ))
        .processorEnd("end", "dumb")))

    val configCreator = new EmptyProcessConfigCreator {
      override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
        Map("typed-source" -> WithCategories.anyCategory(TypedSourceFactory))

      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
        Map("dumb" -> WithCategories.anyCategory(DumbService))
    }
    val modelData = LocalModelData(ConfigFactory.empty(), configCreator)
    val validator = ProcessValidator.default(modelData.processWithObjectsDefinition, new SimpleDictRegistry(Map.empty))
    val validationResult = validator.validate(scenario)
    validationResult
  }
}

object TypedSourceFactory extends SourceFactory {
  @MethodToInvoke
  def invoke(@ParamName("value") value: LazyParameter[AnyRef]): Source =
    new LiteSource[Any] with ReturningType {
      override def createTransformation[F[_] : Monad](evaluateLazyParameter: customComponentTypes.CustomComponentContext[F]): Any => ValidatedNel[ErrorType, Context] = ???
      override def returnType: typing.TypingResult = value.returnType
    }
}

object DumbService extends Service {
  @MethodToInvoke
  def invoke(): Future[Any] = ???
}