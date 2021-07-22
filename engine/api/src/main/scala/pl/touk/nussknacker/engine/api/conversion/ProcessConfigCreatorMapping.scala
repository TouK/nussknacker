package pl.touk.nussknacker.engine.api.conversion

import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}

object ProcessConfigCreatorMapping {

  import scala.collection.JavaConverters._

    def toProcessConfigCreator(creator: pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator): ProcessConfigCreator =
      javaToScala(creator)

  private def javaToScala(jcreator: pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator): ProcessConfigCreator = {
    val creator = new ProcessConfigCreator {
      override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
        jcreator.customStreamTransformers(processObjectDependencies).asScala.toMap
      }
      override def services(processObjectDependencies: ProcessObjectDependencies) : Map[String, WithCategories[Service]] = {
        jcreator.services(processObjectDependencies).asScala.toMap
      }
      override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
        jcreator.sourceFactories(processObjectDependencies).asScala.toMap
      }
      override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
        jcreator.sinkFactories(processObjectDependencies).asScala.toMap
      }
      override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = {
        jcreator.listeners(processObjectDependencies).asScala.toSeq
      }
      override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies) : ExceptionHandlerFactory = {
        jcreator.exceptionHandlerFactory(processObjectDependencies)
      }
      override def expressionConfig(processObjectDependencies: ProcessObjectDependencies) = {
        val jec = jcreator.expressionConfig(processObjectDependencies)
        ExpressionConfig(
          globalProcessVariables = jec.getGlobalProcessVariables.asScala.toMap,
          globalImports = jec.getGlobalImports.asScala.toList,
          additionalClasses = jec.getAdditionalClasses.asScala.toList,
          languages = jec.getLanguages,
          optimizeCompilation = jec.isOptimizeCompilation,
          strictTypeChecking = jec.isStrictTypeChecking,
          dictionaries = jec.getDictionaries.asScala.toMap,
          hideMetaVariable = jec.isHideMetaVariable,
          disableMethodExecutionForUnknown = jec.isDisableMethodExecutionForUnknown
        )
      }
      override def buildInfo(): Map[String, String] = {
        jcreator.buildInfo().asScala.toMap
      }
      override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]] = {
        jcreator.signals(processObjectDependencies).asScala.toMap
      }
      override def asyncExecutionContextPreparer(processObjectDependencies: ProcessObjectDependencies): Option[AsyncExecutionContextPreparer] = {
        Option(jcreator.asyncExecutionContextPreparer(processObjectDependencies).orElse(null))
      }
      override def classExtractionSettings(processObjectDependencies: ProcessObjectDependencies): ClassExtractionSettings = {
        val jSettings = jcreator.classExtractionSettings(processObjectDependencies)
        ClassExtractionSettings(
          jSettings.getExcludeClassPredicates.asScala,
          jSettings.getExcludeClassMemberPredicates.asScala,
          jSettings.getIncludeClassMemberPredicates.asScala,
          jSettings.getPropertyExtractionStrategy)
      }
    }
    creator
  }

}
