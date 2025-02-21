package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}

/**
  * There Nussknacker fetches information about user defined model.
  * Any invocation of user defined logic or resource goes through this class.
  */
trait ProcessConfigCreator extends Serializable {

  def customStreamTransformers(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]]

  def services(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]]

  def sourceFactories(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]]

  def sinkFactories(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]]

  def listeners(modelDependencies: ProcessObjectDependencies): Seq[ProcessListener]

  def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig

  def modelInfo(): ModelInfo

  def asyncExecutionContextPreparer(
      modelDependencies: ProcessObjectDependencies
  ): Option[AsyncExecutionContextPreparer] = None

  def classExtractionSettings(modelDependencies: ProcessObjectDependencies): ClassExtractionSettings =
    ClassExtractionSettings.Default

}
