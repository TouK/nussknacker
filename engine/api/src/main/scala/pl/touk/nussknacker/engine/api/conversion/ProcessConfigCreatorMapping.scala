package pl.touk.nussknacker.engine.api.conversion

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}

object ProcessConfigCreatorMapping {

  import scala.collection.JavaConverters._

    def toProcessConfigCreator(creator: pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator): ProcessConfigCreator =
      javaToScala(creator)

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
      override def expressionConfig(config: Config) = {
        val jec = jcreator.expressionConfig(config)
        ExpressionConfig(
          globalProcessVariables = jec.getGlobalProcessVariables.asScala.toMap,
          globalImports = jec.getGlobalImports.asScala.toList,
          languages = jec.getLanguages,
          optimizeCompilation = jec.isOptimizeCompilation,
          strictTypeChecking = jec.isStrictTypeChecking,
          dictionaries = jec.getDictionaries.asScala.toMap,
          hideMetaVariable = jec.isHideMetaVariable
        )
      }
      override def buildInfo(): Map[String, String] = {
        jcreator.buildInfo().asScala.toMap
      }
      override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = {
        jcreator.signals(config).asScala.toMap
      }
      override def asyncExecutionContextPreparer(config: Config): Option[AsyncExecutionContextPreparer] = {
        Option(jcreator.asyncExecutionContextPreparer(config).orElse(null))
      }
      override def classExtractionSettings(config: Config): ClassExtractionSettings = {
        val jSettings = jcreator.classExtractionSettings(config)
        ClassExtractionSettings(jSettings.getBlacklistedClassMemberPredicates.asScala)
      }
    }
    creator
  }

}