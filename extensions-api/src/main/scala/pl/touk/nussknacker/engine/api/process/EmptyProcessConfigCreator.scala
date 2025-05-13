package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo

class EmptyProcessConfigCreator extends ProcessConfigCreator {

  override def customStreamTransformers(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[CustomStreamTransformer]] =
    Map.empty

  override def services(modelConfig: ModelConfig): Map[String, WithCategories[Service]] =
    Map.empty

  override def sourceFactories(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[SourceFactory]] =
    Map.empty

  override def sinkFactories(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[SinkFactory]] =
    Map.empty

  override def listeners(modelConfig: ModelConfig): Seq[ProcessListener] =
    Nil

  override def expressionConfig(modelConfig: ModelConfig) =
    ExpressionConfig(Map.empty, List.empty)

  override def modelInfo(): ModelInfo =
    ModelInfo.empty

}
