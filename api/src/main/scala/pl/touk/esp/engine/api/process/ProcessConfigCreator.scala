package pl.touk.esp.engine.api.process

import com.typesafe.config.Config
import pl.touk.esp.engine.api.exception.ExceptionHandlerFactory
import pl.touk.esp.engine.api.{CustomStreamTransformer, ProcessListener, Service}

trait ProcessConfigCreator extends Serializable {

  def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]]

  def services(config: Config) : Map[String, WithCategories[Service]]

  def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]]

  def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]]

  def listeners(config: Config): Seq[ProcessListener]

  def exceptionHandlerFactory(config: Config) : ExceptionHandlerFactory

  def globalProcessVariables(config: Config): Map[String, WithCategories[Class[_]]]

  def buildInfo(): Map[String, String]

}
