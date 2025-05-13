package pl.touk.nussknacker.engine.api.conversion

import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.javaapi.process

import scala.jdk.CollectionConverters._

object ProcessConfigCreatorMapping {

  def toProcessConfigCreator(creator: process.ProcessConfigCreator): ProcessConfigCreator =
    javaToScala(creator)

  private def javaToScala(jcreator: process.ProcessConfigCreator): ProcessConfigCreator = {
    val creator = new ProcessConfigCreator {
      override def customStreamTransformers(
          modelConfig: ModelConfig
      ): Map[String, WithCategories[CustomStreamTransformer]] = {
        jcreator.customStreamTransformers(modelConfig).asScala.toMap
      }
      override def services(
          modelConfig: ModelConfig
      ): Map[String, WithCategories[Service]] = {
        jcreator.services(modelConfig).asScala.toMap
      }
      override def sourceFactories(
          modelConfig: ModelConfig
      ): Map[String, WithCategories[SourceFactory]] = {
        jcreator.sourceFactories(modelConfig).asScala.toMap
      }
      override def sinkFactories(
          modelConfig: ModelConfig
      ): Map[String, WithCategories[SinkFactory]] = {
        jcreator.sinkFactories(modelConfig).asScala.toMap
      }
      override def listeners(modelConfig: ModelConfig): Seq[ProcessListener] = {
        jcreator.listeners(modelConfig).asScala.toSeq
      }
      override def expressionConfig(modelConfig: ModelConfig): ExpressionConfig = {
        val jec = jcreator.expressionConfig(modelConfig)
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
          modelConfig: ModelConfig
      ): Option[AsyncExecutionContextPreparer] = {
        Option(jcreator.asyncExecutionContextPreparer(modelConfig).orElse(null))
      }
      override def classExtractionSettings(
          modelConfig: ModelConfig
      ): ClassExtractionSettings = {
        val jSettings = jcreator.classExtractionSettings(modelConfig)
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
