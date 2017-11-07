package pl.touk.nussknacker.engine.standalone

import argonaut.Json
import cats.data.{NonEmptyList, Validated}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{Context, Displayable}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.compile.ProcessCompilationError
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter.GenericResultType
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer

import scala.concurrent.{ExecutionContext, Future}

object StandaloneRequestHandler {

  def apply(process: EspProcess, contextPreparer: StandaloneContextPreparer, modelData: ModelData)
    : Validated[NonEmptyList[ProcessCompilationError], StandaloneRequestHandler]= {
    modelData.withThisAsContextClassLoader {
      StandaloneProcessInterpreter(process, contextPreparer, modelData).map(new StandaloneRequestHandler(_))
    }
  }

}

//this class handles parsing, displaying and invoking interpreter. This is the only place we interact with model, hence
//only here we care about context classloaders
class StandaloneRequestHandler(standaloneProcessInterpreter: StandaloneProcessInterpreter) {

  type Result[T] = GenericResultType[T, EspExceptionInfo[_<:Throwable]]

  def invoke(bytes: Array[Byte])(implicit ec: ExecutionContext) : Future[Result[Json]] = standaloneProcessInterpreter.modelData.withThisAsContextClassLoader {
    val sourceObject = standaloneProcessInterpreter.source.toObject(bytes)
    standaloneProcessInterpreter.invoke(sourceObject).map(toResponse)
  }

  def close(): Unit = standaloneProcessInterpreter.modelData.withThisAsContextClassLoader {
    standaloneProcessInterpreter.close()
  }

  def open(): Unit = standaloneProcessInterpreter.modelData.withThisAsContextClassLoader {
    standaloneProcessInterpreter.open()
  }

  def id : String = standaloneProcessInterpreter.id

  private def toResponse(result: Result[Any]) : Result[Json] = {

    val withJsonsConverted  : Result[Result[Json]] = result.right.map(toJsonOrErrors)

    withJsonsConverted.right.flatMap[NonEmptyList[EspExceptionInfo[_<:Throwable]], List[Json]](StandaloneProcessInterpreter.foldResults)
  }

  private def toJsonOrErrors(values: List[Any]) : List[Result[Json]] = values.map(toJsonOrError)

  private def toJsonOrError(value: Any) : Result[Json] = value match {
    case a:Displayable => Right(List(a.display))
    case a:String => Right(List(Json.jString(a)))
    case a => Left(NonEmptyList.of(EspExceptionInfo(None, new IllegalArgumentException(s"Invalid result type: ${a.getClass}"),
      Context(""))))
  }

}
