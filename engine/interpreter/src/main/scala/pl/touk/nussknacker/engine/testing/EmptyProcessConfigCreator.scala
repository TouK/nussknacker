package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}

class EmptyProcessConfigCreator
  extends ProcessConfigCreator {

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] =
    Map.empty

  override def services(config: Config): Map[String, WithCategories[Service]] =
    Map.empty

  override def sourceFactories(config: Config, objectNaming: ObjectNaming): Map[String, WithCategories[SourceFactory[_]]] =
    Map.empty

  override def sinkFactories(config: Config, objectNaming: ObjectNaming): Map[String, WithCategories[SinkFactory]] =
    Map.empty

  override def listeners(config: Config): Seq[ProcessListener] =
    Nil

  //TODO: this does not work for Flink procsses -> as it is doesn't define restart strategy...
  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(_ => EspExceptionHandler.empty)

  override def expressionConfig(config: Config) = ExpressionConfig(Map.empty, List.empty, LanguageConfiguration.default)

  override def buildInfo(): Map[String, String] =
    Map.empty

  override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] =
    Map.empty
}
