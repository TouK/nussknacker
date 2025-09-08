package pl.touk.nussknacker.engine.schemedkafka.helpers

import cats.data.ValidatedNel
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.Assertion
import pl.touk.nussknacker.engine.{RuntimeMode, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, NodeCompiler}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.node.{Source => SourceNode}
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ExistingSchemaVersion,
  LatestSchemaVersion,
  SchemaRegistryClientFactory,
  SchemaVersionOption
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

trait KafkaSourceSpecificTestDataMixin {
  self: KafkaSchemaRegistryMixin with LazyLogging =>

  type KafkaSource = SourceFactory with KafkaUniversalComponentTransformer[Source, TopicName.ForSource]

  protected def schemaRegistryClientFactory: SchemaRegistryClientFactory

  override protected def kafkaComponentsConfigPrefix: String = "not-important"

  protected def universalSourceFactory(useStringForKey: Boolean): KafkaSource = {
    val kafkaConfigWithCorrectUseStringForKey = kafkaComponentsConfig.copy(useStringForKey = useStringForKey)
    val universalPayload =
      UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory, kafkaConfigWithCorrectUseStringForKey)
    new UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      universalPayload,
      kafkaConfigWithCorrectUseStringForKey,
      NamingStrategy.Disabled,
      new FlinkKafkaSourceImplFactory
    )
  }

  protected def roundTripKeyValueObject(
      sourceFactory: Boolean => KafkaSource,
      useStringForKey: Boolean,
      topic: String,
      versionOption: SchemaVersionOption,
      givenKey: Any,
      givenValue: Any
  ): Assertion = {
    pushMessageWithKey(givenKey, givenValue, topic, useStringForKey = useStringForKey)
    val compiledSource = compileSource(
      sourceFactory(useStringForKey),
      prepareNodeParameters(topic, versionOption)
    ).validValue
    readLastMessageAndVerify(compiledSource, givenKey, givenValue)
  }

  protected def readLastMessageAndVerify(
      compiledSource: Source with TestDataGenerator with SourceTestSupport[AnyRef],
      givenKey: Any,
      givenValue: Any
  ): Assertion = {
    val testData = compiledSource.generateTestData(1)
    logger.info("test object: " + testData)
    val deserializedObj =
      compiledSource.testRecordParser.parse(testData.testRecords).head.asInstanceOf[ConsumerRecord[Any, Any]]

    deserializedObj.key() shouldEqual givenKey
    deserializedObj.value() shouldEqual givenValue
  }

  protected def compileSource(
      sourceFactory: KafkaSource,
      nodeParameters: List[NodeParameter]
  ): ValidatedNel[ProcessCompilationError, Source with TestDataGenerator with SourceTestSupport[AnyRef]] = {
    val modelData = LocalModelData(ConfigFactory.empty(), List(ComponentDefinition("kafka", sourceFactory)))
    val nodeCompiler = new NodeCompiler(
      definitions = modelData.modelDefinition,
      fragmentDefinitionExtractor = new FragmentParametersDefinitionExtractor(
        modelData.modelClassLoader,
        modelData.modelDefinitionWithClasses.classDefinitions,
        modelData.modelConfig.globalParametersConfig
      ),
      expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper,
      classLoader = modelData.modelClassLoader,
      listeners = Seq.empty,
      resultCollector = ProductionServiceInvocationCollector,
      runtimeMode = RuntimeMode.Live,
      nodesDeploymentData = NodesDeploymentData.empty,
      nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default
    )

    nodeCompiler
      .compileSource(SourceNode("mock-id", SourceRef("kafka", nodeParameters)))(
        new ScenarioCompilationDependencies(
          JobData(MetaData("mock-id", StreamMetaData()), ProcessVersion.empty),
          EngineScenarioCompilationDependencies.empty
        )
      )
      .compiledObject
      .map(_.asInstanceOf[Source with TestDataGenerator with FlinkSourceTestSupport[AnyRef]])
  }

  protected def prepareNodeParameters(
      topic: ProcessingType,
      versionOption: SchemaVersionOption
  ): List[NodeParameter] = {
    List(
      NodeParameter(KafkaUniversalComponentTransformer.topicParamName, s"'$topic'".spel),
      NodeParameter(
        KafkaUniversalComponentTransformer.schemaVersionParamName,
        s"'${versionOptionToString(versionOption)}'".spel
      )
    )
  }

  protected def versionOptionToString(versionOption: SchemaVersionOption): String = {
    versionOption match {
      case LatestSchemaVersion      => SchemaVersionOption.LatestOptionName
      case ExistingSchemaVersion(v) => v.toString
    }
  }

}
