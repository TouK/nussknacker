package pl.touk.esp.engine.api.process

import com.typesafe.config.Config
import pl.touk.esp.engine.api.{EspExceptionHandler, FoldingFunction, ProcessListener, Service}

trait ProcessConfigCreator extends Serializable {

  def services(config: Config) : Map[String, Service]

  def sourceFactories(config: Config): Map[String, SourceFactory[_]]

  def sinkFactories(config: Config): Map[String, SinkFactory]

  def listeners(config: Config): Seq[ProcessListener]

  def foldingFunctions(config: Config) : Map[String, FoldingFunction[_]]

  def exceptionHandler(config: Config) : EspExceptionHandler
}
