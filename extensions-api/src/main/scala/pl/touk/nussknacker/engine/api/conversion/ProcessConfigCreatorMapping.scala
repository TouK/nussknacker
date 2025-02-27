package pl.touk.nussknacker.engine.api.conversion

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.javaapi.process

import scala.jdk.CollectionConverters._

object ProcessConfigCreatorMapping {

  def toProcessConfigCreator(creator: process.ProcessConfigCreator): ProcessConfigCreator =
    javaToScala(creator)

  private def javaToScala(jcreator: process.ProcessConfigCreator): ProcessConfigCreator = {
    val creator = new ProcessConfigCreator {
      override def customStreamTransformers(
          modelDependencies: ProcessObjectDependencies
      ): Map[String, WithCategories[CustomStreamTransformer]] = {
        jcreator.customStreamTransformers(modelDependencies).asScala.toMap
      }
      override def services(
          modelDependencies: ProcessObjectDependencies
      ): Map[String, WithCategories[Service]] = {
        jcreator.services(modelDependencies).asScala.toMap
      }
      override def sourceFactories(
          modelDependencies: ProcessObjectDependencies
      ): Map[String, WithCategories[SourceFactory]] = {
        jcreator.sourceFactories(modelDependencies).asScala.toMap
      }
      override def sinkFactories(
          modelDependencies: ProcessObjectDependencies
      ): Map[String, WithCategories[SinkFactory]] = {
        jcreator.sinkFactories(modelDependencies).asScala.toMap
      }
      override def listeners(modelDependencies: ProcessObjectDependencies): Seq[ProcessListener] = {
        jcreator.listeners(modelDependencies).asScala.toSeq
      }
      override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig = {
        val jec = jcreator.expressionConfig(modelDependencies)
        ExpressionConfig(
          globalProcessVariables = jec.getGlobalProcessVariables.asScala.toMap,
          globalImports = jec.getGlobalImports.asScala.toList,
          additionalClasses = jec.getAdditionalClasses.asScala.toList,
          optimizeCompilation = jec.isOptimizeCompilation,
          dictionaries = jec.getDictionaries.asScala.toMap,
          hideMetaVariable = jec.isHideMetaVariable,
          methodExecutionForUnknownAllowed = jec.isMethodExecutionForUnknownAllowed
        )
      }
      override def modelInfo(): ModelInfo = {
        ModelInfo.fromMap(jcreator.modelInfo().asScala.toMap)
      }
      override def asyncExecutionContextPreparer(
          modelDependencies: ProcessObjectDependencies
      ): Option[AsyncExecutionContextPreparer] = {
        Option(jcreator.asyncExecutionContextPreparer(modelDependencies).orElse(null))
      }
      override def classExtractionSettings(
          modelDependencies: ProcessObjectDependencies
      ): ClassExtractionSettings = {
        val jSettings = jcreator.classExtractionSettings(modelDependencies)
        ClassExtractionSettings(
          jSettings.getExcludeClassPredicates.asScala.toSeq,
          jSettings.getExcludeClassMemberPredicates.asScala.toSeq,
          jSettings.getIncludeClassMemberPredicates.asScala.toSeq,
          jSettings.getTypingFunctionRules.asScala.toSeq.map { entry =>
            TypingFunctionRule(entry.getKey, entry.getValue)
          },
          jSettings.getPropertyExtractionStrategy
        )
      }
    }
    creator
  }

}
