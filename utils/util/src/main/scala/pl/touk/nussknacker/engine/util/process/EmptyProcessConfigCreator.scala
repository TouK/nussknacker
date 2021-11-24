package pl.touk.nussknacker.engine.util.process

import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}

class EmptyProcessConfigCreator
  extends ProcessConfigCreator {

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
    Map.empty

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
    Map.empty

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
    Map.empty

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
    Map.empty

  override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    Nil

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies) =
    ExpressionConfig(Map.empty, List.empty)

  override def buildInfo(): Map[String, String] =
    Map.empty

  override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]] =
    Map.empty
}
