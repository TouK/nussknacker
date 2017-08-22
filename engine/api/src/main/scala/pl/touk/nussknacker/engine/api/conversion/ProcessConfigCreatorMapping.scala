package pl.touk.nussknacker.engine.api.conversion

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}

object ProcessConfigCreatorMapping {

  import scala.collection.JavaConverters._

  def toProcessConfigCreator(creator: Any): ProcessConfigCreator = {
    creator match {
      case scalaCreator: ProcessConfigCreator =>
        scalaCreator
      case javaCreator: pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator =>
        javaToScala(javaCreator)
    }
  }

  private def javaToScala(jcreator: pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator): ProcessConfigCreator = {
    val creator = new ProcessConfigCreator {
      override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = {
        jcreator.customStreamTransformers(config).asScala.toMap
      }
      override def services(config: Config) : Map[String, WithCategories[Service]] = {
        jcreator.services(config).asScala.toMap
      }
      override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = {
        jcreator.sourceFactories(config).asScala.toMap
      }
      override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = {
        jcreator.sinkFactories(config).asScala.toMap
      }
      override def listeners(config: Config): Seq[ProcessListener] = {
        jcreator.listeners(config).asScala.toSeq
      }
      override def exceptionHandlerFactory(config: Config) : ExceptionHandlerFactory = {
        jcreator.exceptionHandlerFactory(config)
      }
      override def globalProcessVariables(config: Config): Map[String, WithCategories[AnyRef]] = {
        jcreator.globalProcessVariables(config).asScala.toMap
      }
      override def buildInfo(): Map[String, String] = {
        jcreator.buildInfo().asScala.toMap
      }
      override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = {
        jcreator.signals(config).asScala.toMap
      }
    }
    creator
  }
}