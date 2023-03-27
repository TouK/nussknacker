package pl.touk.nussknacker.engine.api.conversion

import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}
import pl.touk.nussknacker.engine.javaapi.process

import scala.jdk.CollectionConverters._

object ProcessConfigCreatorMapping {


    def toProcessConfigCreator(creator: process.ProcessConfigCreator): ProcessConfigCreator =
      javaToScala(creator)

  private def javaToScala(jcreator: process.ProcessConfigCreator): ProcessConfigCreator = {
    val creator = new ProcessConfigCreator {
      override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
        jcreator.customStreamTransformers(processObjectDependencies).asScala.toMap
      }
      override def services(processObjectDependencies: ProcessObjectDependencies) : Map[String, WithCategories[Service]] = {
        jcreator.services(processObjectDependencies).asScala.toMap
      }
      override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
        jcreator.sourceFactories(processObjectDependencies).asScala.toMap
      }
      override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
        jcreator.sinkFactories(processObjectDependencies).asScala.toMap
      }
      override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = {
        jcreator.listeners(processObjectDependencies).asScala.toSeq
      }
      override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
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
          methodExecutionForUnknownAllowed = jec.isMethodExecutionForUnknownAllowed
        )
      }
      override def buildInfo(): Map[String, String] = {
        jcreator.buildInfo().asScala.toMap
      }
      override def asyncExecutionContextPreparer(processObjectDependencies: ProcessObjectDependencies): Option[AsyncExecutionContextPreparer] = {
        Option(jcreator.asyncExecutionContextPreparer(processObjectDependencies).orElse(null))
      }
      override def classExtractionSettings(processObjectDependencies: ProcessObjectDependencies): ClassExtractionSettings = {
        val jSettings = jcreator.classExtractionSettings(processObjectDependencies)
        ClassExtractionSettings(
          jSettings.getExcludeClassPredicates.asScala.toSeq,
          jSettings.getExcludeClassMemberPredicates.asScala.toSeq,
          jSettings.getIncludeClassMemberPredicates.asScala.toSeq,
          jSettings.getPropertyExtractionStrategy)
      }
    }
    creator
  }

}
