package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.{Context, LazyParameterInterpreter}
//import pl.touk.nussknacker.engine.standalone.api.types.{InternalInterpreterOutputType, InterpreterOutputType, InterpreterType}

import scala.concurrent.ExecutionContext

/*
sealed trait BaseStandaloneCustomTransformer {

  type StandaloneCustomTransformation

  def createTransformation(outputVariable: Option[String]) : StandaloneCustomTransformation

}

trait StandaloneCustomTransformer extends BaseStandaloneCustomTransformer {

  type StandaloneCustomTransformation = (InterpreterType, LazyParameterInterpreter) => InterpreterType

}

trait JoinStandaloneCustomTransformer extends BaseStandaloneCustomTransformer {

  type StandaloneCustomTransformation = (InterpreterType, LazyParameterInterpreter) => (Map[String, List[Context]], ExecutionContext) => InternalInterpreterOutputType

} */