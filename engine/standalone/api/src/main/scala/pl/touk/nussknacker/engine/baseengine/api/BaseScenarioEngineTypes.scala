package pl.touk.nussknacker.engine.baseengine.api

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.{Context, JoinReference, LazyParameter, LazyParameterInterpreter}

import scala.language.higherKinds

object BaseScenarioEngineTypes {

  type CustomTransformerData = LazyParameterInterpreter

  type ErrorType = NonEmptyList[EspExceptionInfo[_ <: Throwable]]

  type GenericResultType[T] = Either[ErrorType, T]

  type GenericListResultType[T] = GenericResultType[List[T]]

  type InterpretationResultType[Result] = GenericListResultType[EndResult[Result]]

  type InternalInterpreterOutputType[F[_]] = F[GenericListResultType[PartResultType]]

  type InterpreterType[F[_]] = List[Context] => InternalInterpreterOutputType[F]

  case class SourceId(value: String)

  sealed trait PartResultType

  case class EndResult[Result](nodeId: String, context: Context, result: Result) extends PartResultType

  case class JoinResult(reference: JoinReference, context: Context) extends PartResultType

  sealed trait BaseCustomTransformer {

    type CustomTransformation

    def createTransformation(outputVariable: Option[String]): CustomTransformation

  }

  trait CustomTransformer[F[_]] extends BaseCustomTransformer {

    type CustomTransformation = (InterpreterType[F], CustomTransformerData) => InterpreterType[F]

  }

  trait JoinCustomTransformer[F[_]] extends BaseCustomTransformer {

    type CustomTransformation = (InterpreterType[F], CustomTransformerData) => Map[String, List[Context]] => InternalInterpreterOutputType[F]

  }

  trait BaseEngineSink extends Sink {

    //TODO: enable using outputExpression?
    def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef]

  }

}
