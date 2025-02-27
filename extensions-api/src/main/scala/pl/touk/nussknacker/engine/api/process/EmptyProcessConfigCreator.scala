package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo

class EmptyProcessConfigCreator extends ProcessConfigCreator {

  override def customStreamTransformers(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] =
    Map.empty

  override def services(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
    Map.empty

  override def sourceFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] =
    Map.empty

  override def sinkFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] =
    Map.empty

  override def listeners(modelDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    Nil

  override def expressionConfig(modelDependencies: ProcessObjectDependencies) =
    ExpressionConfig(Map.empty, List.empty)

  override def modelInfo(): ModelInfo =
    ModelInfo.empty

}
