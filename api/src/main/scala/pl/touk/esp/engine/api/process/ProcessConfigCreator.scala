package pl.touk.esp.engine.api.process

import com.typesafe.config.Config
import pl.touk.esp.engine.api.exception.ExceptionHandlerFactory
import pl.touk.esp.engine.api.{CustomStreamTransformer, FoldingFunction, ProcessListener, Service}

trait ProcessConfigCreator extends Serializable {

  def customStreamTransformers(config: Config): Map[String, CustomStreamTransformer]

  def services(config: Config) : Map[String, Service]

  def sourceFactories(config: Config): Map[String, SourceFactory[_]]

  def sinkFactories(config: Config): Map[String, SinkFactory]

  def listeners(config: Config): Seq[ProcessListener]

  def foldingFunctions(config: Config) : Map[String, FoldingFunction[_]]

  def exceptionHandlerFactory(config: Config) : ExceptionHandlerFactory

}
