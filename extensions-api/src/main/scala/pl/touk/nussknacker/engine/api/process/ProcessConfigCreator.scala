package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}

/**
  * There Nussknacker fetches information about user defined model.
  * Any invocation of user defined logic or resource goes through this class.
  */
trait ProcessConfigCreator extends Serializable {

  def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]]

  def services(processObjectDependencies: ProcessObjectDependencies) : Map[String, WithCategories[Service]]

  def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]]

  def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]]

  def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener]

  def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig

  def buildInfo(): Map[String, String]

  def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]]

  def asyncExecutionContextPreparer(processObjectDependencies: ProcessObjectDependencies): Option[AsyncExecutionContextPreparer] = None

  def classExtractionSettings(processObjectDependencies: ProcessObjectDependencies): ClassExtractionSettings = ClassExtractionSettings.Default

}
