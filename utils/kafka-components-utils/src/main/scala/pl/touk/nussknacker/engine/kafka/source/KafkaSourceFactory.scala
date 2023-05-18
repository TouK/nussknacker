package pl.touk.nussknacker.engine.kafka.source

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.kafka.KafkaFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaDeserializationSchema, KafkaDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory._
import pl.touk.nussknacker.engine.kafka.validator.WithCachedTopicsExistenceValidator

import scala.reflect.ClassTag

/**
  * Base factory for Kafka sources with additional metadata variable.
  * It is based on [[pl.touk.nussknacker.engine.api.context.transformation.SingleInputGenericNodeTransformation]]
  * that allows custom ValidationContext and Context transformations, which are provided by [[KafkaContextInitializer]]
  * Can be used for single- or multi- topic sources (as csv, see topicNameSeparator and extractTopics).
  *
  * Features:
  *   - fetch latest N records which can be later used to test process in UI
  *     Fetching data is defined in source which may
  *     extends [[pl.touk.nussknacker.engine.api.process.TestDataGenerator]]. See [[pl.touk.nussknacker.engine.kafka.KafkaUtils#readLastMessages]]
  *   - reset Kafka's offset to latest value - `forceLatestRead` property, see [[pl.touk.nussknacker.engine.kafka.KafkaUtils#setOffsetToLatest]]
  *
  * @tparam K - type of key of kafka event that is generated by raw source (SourceFunction).
  * @tparam V - type of value of kafka event that is generated by raw source (SourceFunction).
  * */
class KafkaSourceFactory[K: ClassTag, V: ClassTag](protected val deserializationSchemaFactory: KafkaDeserializationSchemaFactory[ConsumerRecord[K, V]],
                                                   protected val formatterFactory: RecordFormatterFactory,
                                                   protected val processObjectDependencies: ProcessObjectDependencies,
                                                   protected val implProvider: KafkaSourceImplFactory[K, V])
  extends SourceFactory
    with SingleInputGenericNodeTransformation[Source]
    with WithCachedTopicsExistenceValidator
    with WithExplicitTypesToExtract {

  protected val topicNameSeparator = ","

  protected lazy val keyTypingResult: TypedClass = Typed.typedClass[K](implicitly[ClassTag[K]])

  protected lazy val valueTypingResult: TypedClass = Typed.typedClass[V](implicitly[ClassTag[V]])

  // Node validation and compilation refers to ValidationContext, that returns TypingResult's of all variables returned by the source.
  // Variable suggestion uses DefinitionExtractor that requires proper type definitions for GenericNodeTransformation (which in general does not have a specified "returnType"):
  // - for TypeClass (which is a default scenario) - it is necessary to provide all explicit TypeClass definitions as possibleVariableClasses
  // - for TypedObjectTypingResult - suggested variables are defined as explicit "fields"
  // Example:
  // - validation context indicates that #input is TypedClass(classOf(SampleProduct)), that is used by node compilation and validation
  // - definition extractor provides detailed definition of "pl.touk.nussknacker.engine.management.sample.dto.SampleProduct"
  // See also ProcessDefinitionExtractor.
  override def typesToExtract: List[TypedClass] = List(keyTypingResult, valueTypingResult, Typed.typedClass[TimestampType])

  override type State = KafkaSourceFactoryState[K, V]

  protected def handleExceptionInInitialParameters: List[Parameter] = Nil

  private def initialStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case step@TransformationStep(Nil, _) =>
      NextParameters(prepareInitialParameters)
  }

  protected def topicsValidationErrors(topic: String)(implicit nodeId: NodeId): List[ProcessCompilationError.CustomNodeError] = {
      val topics = topic.split(topicNameSeparator).map(_.trim).toList
      val preparedTopics = topics.map(KafkaComponentsUtils.prepareKafkaTopic(_, processObjectDependencies)).map(_.prepared)
      validateTopics(preparedTopics).swap.toList.map(_.toCustomNodeError(nodeId.id, Some(TopicParamName)))
  }

  protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case step@TransformationStep((TopicParamName, DefinedEagerParameter(topic: String, _)) :: _, None) =>
      prepareSourceFinalResults(context, dependencies, step.parameters, keyTypingResult, valueTypingResult, topicsValidationErrors(topic))
    case step@TransformationStep((TopicParamName, _) :: _, None) =>
      // Edge case - for some reason Topic is not defined, e.g. when topic does not match DefinedEagerParameter(String, _):
      // 1. FailedToDefineParameter
      // 2. not resolved as a valid String
      // Those errors are identified by parameter validation and handled elsewhere, hence empty list of errors.
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

  protected def prepareSourceFinalResults(context: ValidationContext,
                                          dependencies: List[NodeDependencyValue],
                                          parameters: List[(String, DefinedParameter)],
                                          keyTypingResult: TypingResult,
                                          valueTypingResult: TypingResult,
                                          errors: List[ProcessCompilationError]
                                         )(implicit nodeId: NodeId): FinalResults = {
    val kafkaContextInitializer = prepareContextInitializer(dependencies, parameters, keyTypingResult, valueTypingResult)
    FinalResults.forValidation(context, errors, Some(KafkaSourceFactoryState(kafkaContextInitializer)))(kafkaContextInitializer.validationContext)
  }

  // Source specific FinalResults with errors
  protected def prepareSourceFinalErrors(context: ValidationContext,
                                         dependencies: List[NodeDependencyValue],
                                         parameters: List[(String, DefinedParameter)],
                                         errors: List[ProcessCompilationError])(implicit nodeId: NodeId): FinalResults = {
    val initializerWithUnknown = prepareContextInitializer(dependencies, parameters, Unknown, Unknown)
    FinalResults.forValidation(context, errors)(initializerWithUnknown.validationContext)
  }

  // Overwrite this for dynamic type definitions.
  protected def prepareContextInitializer(dependencies: List[NodeDependencyValue],
                                          parameters: List[(String, DefinedParameter)],
                                          keyTypingResult: TypingResult,
                                          valueTypingResult: TypingResult): ContextInitializer[ConsumerRecord[K, V]] =
    new KafkaContextInitializer[K, V](OutputVariableNameDependency.extract(dependencies), keyTypingResult, valueTypingResult)

  /**
    * contextTransformation should handle exceptions raised by prepareInitialParameters
    */
  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId)
  : NodeTransformationDefinition =
    initialStep(context, dependencies) orElse
      nextSteps(context ,dependencies)

  /**
    * Common set of operations required to create basic KafkaSource.
    */
  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): Source = {
    val topics = extractTopics(params)
    val preparedTopics = topics.map(KafkaComponentsUtils.prepareKafkaTopic(_, processObjectDependencies))
    val deserializationSchema = deserializationSchemaFactory.create(topics, kafkaConfig)
    val formatter = formatterFactory.create(kafkaConfig, deserializationSchema)
    val contextInitializer = finalState.get.contextInitializer
    implProvider.createSource(params, dependencies, finalState.get, preparedTopics, kafkaConfig, deserializationSchema, formatter, contextInitializer, KafkaTestParametersInfo.empty)
  }

  /**
    * Basic implementation of definition of single topic parameter.
    * In case of fetching topics from external repository: return list of topics or raise exception.
    */
  protected def prepareInitialParameters: List[Parameter] = topicParameter.parameter :: Nil

  protected val topicParameter: ParameterWithExtractor[String] =
    ParameterWithExtractor.mandatory[String](
      TopicParamName,
      _.copy(validators = List(MandatoryParameterValidator, NotBlankParameterValidator))
    )

  /**
    * Extracts topics from default topic parameter.
    */
  protected def extractTopics(params: Map[String, Any]): List[String] = {
    val paramValue = topicParameter.extractValue(params)
    paramValue.split(topicNameSeparator).map(_.trim).toList
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData],
    TypedNodeDependency[NodeId], OutputVariableNameDependency)

  override protected val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config)
}

object KafkaSourceFactory {

  case class KafkaSourceFactoryState[K, V](contextInitializer: ContextInitializer[ConsumerRecord[K, V]])

  trait KafkaSourceImplFactory[K, V] {

    def createSource(params: Map[String, Any],
                     dependencies: List[NodeDependencyValue],
                     finalState: Any,
                     preparedTopics: List[PreparedKafkaTopic],
                     kafkaConfig: KafkaConfig,
                     deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                     formatter: RecordFormatter,
                     contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
                     testParametersInfo: KafkaTestParametersInfo): Source

  }

}
