package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo

/**
  * There Nussknacker fetches information about user defined model.
  * Any invocation of user defined logic or resource goes through this class.
  */
trait ProcessConfigCreator extends Serializable {

  def customStreamTransformers(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[CustomStreamTransformer]]

  def services(modelConfig: ModelConfig): Map[String, WithCategories[Service]]

  def sourceFactories(modelConfig: ModelConfig): Map[String, WithCategories[SourceFactory]]

  def sinkFactories(modelConfig: ModelConfig): Map[String, WithCategories[SinkFactory]]

  def listeners(modelConfig: ModelConfig): Seq[ProcessListener]

  def expressionConfig(modelConfig: ModelConfig): ExpressionConfig

  def modelInfo(): ModelInfo

  def asyncExecutionContextPreparer(
      modelConfig: ModelConfig
  ): Option[AsyncExecutionContextPreparer] = None

  def classExtractionSettings(modelConfig: ModelConfig): ClassExtractionSettings =
    ClassExtractionSettings.Default

}
