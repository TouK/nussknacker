package pl.touk.nussknacker.engine.api.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender

/**
  * There Nussknacker fetches information about user defined model.
  * Any invocation of user defined logic or resource goes through this class.
  */
trait ProcessConfigCreator extends Serializable {

  def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]]

  def services(config: Config) : Map[String, WithCategories[Service]]

  def sourceFactories(config: Config, objectNaming: ObjectNaming): Map[String, WithCategories[SourceFactory[_]]]

  def sinkFactories(config: Config, objectNaming: ObjectNaming): Map[String, WithCategories[SinkFactory]]

  def listeners(config: Config): Seq[ProcessListener]

  def exceptionHandlerFactory(config: Config) : ExceptionHandlerFactory

  def expressionConfig(config: Config): ExpressionConfig
  
  def buildInfo(): Map[String, String]

  def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]]

  def asyncExecutionContextPreparer(config: Config): Option[AsyncExecutionContextPreparer] = None

  def classExtractionSettings(config: Config): ClassExtractionSettings = ClassExtractionSettings.Default

}
