package pl.touk.nussknacker.engine.api

import scala.concurrent.{ExecutionContext, Future}

/**
  * Hook for using Apache Flink API directly.
  * See examples in pl.touk.nussknacker.engine.example.custom
**/
//TODO this could be scala-trait, but we leave it as abstract class for now for java compatibility
//We should consider separate interfaces for java implementation, but right now we convert ProcessConfigCreator
//from java to scala one and is seems difficult to convert java CustomStreamTransformer, Service etc. into scala ones
abstract class CustomStreamTransformer {

  //TODO: it is still pretty easy to forget about it. maybe shouldn't be default??
  //maybe should be detected somehow by types??
  def clearsContext : Boolean = false

}

trait LazyInterpreter[T] {

  def createInterpreter(ec: ExecutionContext) : (InterpretationResult => Future[T])

  def syncInterpretationFunction : InterpretationResult => T

}
