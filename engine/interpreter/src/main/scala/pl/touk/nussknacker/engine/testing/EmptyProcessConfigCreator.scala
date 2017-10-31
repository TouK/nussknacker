package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}

class EmptyProcessConfigCreator
  extends ProcessConfigCreator {

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] =
    Map.empty

  override def services(config: Config): Map[String, WithCategories[Service]] =
    Map.empty

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] =
    Map.empty

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] =
    Map.empty

  override def listeners(config: Config): Seq[ProcessListener] =
    Nil

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(_ => EspExceptionHandler.empty)

  override def globalProcessVariables(config: Config): Map[String, WithCategories[AnyRef]] =
    Map.empty

  override def buildInfo(): Map[String, String] =
    Map.empty

  override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] =
    Map.empty
}
