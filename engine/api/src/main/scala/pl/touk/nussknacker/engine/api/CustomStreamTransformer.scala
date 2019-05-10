package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import scala.concurrent.{ExecutionContext, Future}

/**
  * Hook for using Apache Flink API directly.
  * See examples in pl.touk.nussknacker.engine.example.custom
**/
//TODO this could be scala-trait, but we leave it as abstract class for now for java compatibility
//We should consider separate interfaces for java implementation, but right now we convert ProcessConfigCreator
//from java to scala one and is seems difficult to convert java CustomStreamTransformer, Service etc. into scala ones
abstract class CustomStreamTransformer {

  /**
    * deprecated - use ContextTransformation.definedBy(Valid(_.clearVariables)) instead
    */
  // TODO: remove after full switch to ContextTransformation API
  def clearsContext = false

  /**
    * deprecated - use ContextTransformation.join instead
    */
  // TODO: remove after full switch to ContextTransformation API
  def canHaveManyInputs: Boolean = false

}

/**
  * Lazy parameter is representation of parameter of custom node which should be evaluated for each record:
  * ```def execute(@ParamName("keyBy") keyBy: LazyParameter[String], @ParamName ("length") length: String)```
  * In this case, length is computed as constant during process compilation, while keyBy is evaluated for each event
  * Cannot be evaluated directly (no method like 'evaluate',
  * as evaluation may need e.g. lazy variables and we have to take care of lifecycle, to use it see LazyParameterInterpreter
  *
  */
trait LazyParameter[T] {

  //type of parameter, derived from expression. Can be used for dependent types, see PreviousValueTransformer
  def returnType: TypingResult

}


trait LazyParameterInterpreter {

  def createInterpreter[T](parameter: LazyParameter[T]): (ExecutionContext, Context) => Future[T]

  def syncInterpretationFunction[T](parameter: LazyParameter[T]) : Context => T

  def close(): Unit

}

