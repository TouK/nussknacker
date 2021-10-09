package pl.touk.nussknacker.engine.standalone.api

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, JoinReference, LazyParameterInterpreter}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo

import scala.language.higherKinds

trait BaseScenarioEngineTypes [F[_], Result] {

  type CustomTransformerData = LazyParameterInterpreter

  def toRes(interpretationResult: InterpretationResult): Result

  type SuccessfulResultType = List[Result]

  type ErrorType = NonEmptyList[EspExceptionInfo[_ <: Throwable]]

  type GenericResultType[T] = Either[ErrorType, T]

  type GenericListResultType[T] = GenericResultType[List[T]]

  type InterpretationResultType = GenericListResultType[Result]

  type InterpreterOutputType = F[InterpretationResultType]

  type InternalInterpreterOutputType = F[GenericListResultType[PartResultType]]

  type ScenarioInterpreterType = List[(SourceId, Context)] => InternalInterpreterOutputType

  type InterpreterType = List[Context] => InternalInterpreterOutputType

  case class SourceId(value: String)

  sealed trait PartResultType

  case class EndResult(result: Result) extends PartResultType

  case class JoinResult(reference: JoinReference, context: Context) extends PartResultType


  sealed trait BaseStandaloneCustomTransformer {

    type StandaloneCustomTransformation

    def createTransformation(outputVariable: Option[String]): StandaloneCustomTransformation

  }

  trait StandaloneCustomTransformer extends BaseStandaloneCustomTransformer {

    type StandaloneCustomTransformation = (InterpreterType, CustomTransformerData) => InterpreterType

  }

  trait JoinStandaloneCustomTransformer extends BaseStandaloneCustomTransformer {

    type StandaloneCustomTransformation = (InterpreterType, CustomTransformerData) => Map[String, List[Context]] => InternalInterpreterOutputType

  }

}
