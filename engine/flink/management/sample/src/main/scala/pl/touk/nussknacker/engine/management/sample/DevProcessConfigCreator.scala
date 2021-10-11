package pl.touk.nussknacker.engine.management.sample

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser

import java.time.LocalDateTime
import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import net.ceedubs.ficus.Ficus._
import org.apache.flink.api.common.serialization.{DeserializationSchema, SimpleStringSchema}
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactoryWithEditor
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.exception.ConfigurableExceptionHandlerFactory
import pl.touk.nussknacker.engine.flink.util.sink.{EmptySink, SingleValueSinkFactory}
import pl.touk.nussknacker.engine.flink.util.source.{EspDeserializationSchema, ReturningClassInstanceSource, ReturningTestCaseClass}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.SlidingAggregateTransformerV2
import pl.touk.nussknacker.engine.flink.util.transformer.join.SingleSideJoinTransformer
import pl.touk.nussknacker.engine.flink.util.transformer.{TransformStateTransformer, UnionTransformer, UnionWithMemoTransformer}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.consumerrecord.{ConsumerRecordToJsonFormatterFactory, FixedValueDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.sink.KafkaSinkFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.management.sample.dict.{BusinessConfigDictionary, RGBDictionary, TestDictionary}
import pl.touk.nussknacker.engine.management.sample.dto.{ConstantState, SampleProduct}
import pl.touk.nussknacker.engine.management.sample.global.ConfigTypedGlobalVariable
import pl.touk.nussknacker.engine.management.sample.helper.DateProcessHelper
import pl.touk.nussknacker.engine.management.sample.service._
import pl.touk.nussknacker.engine.management.sample.signal.{RemoveLockProcessSignalFactory, SampleSignalHandlingTransformer}
import pl.touk.nussknacker.engine.management.sample.source._
import pl.touk.nussknacker.engine.management.sample.transformer._
import pl.touk.nussknacker.engine.util.LoggingListener

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

object DevProcessConfigCreator {
  val oneElementValue = "One element"

  //This ConfigCreator is used in tests in quite a few places, where we don't have 'real' schema registry and we don't need it.
  val emptyMockedSchemaRegistryProperty = "withMockedConfluent"
}

/**
  * This config creator is for purpose of development, for end-to-end tests
  */
class DevProcessConfigCreator extends ProcessConfigCreator {

  private def features[T](value: T): WithCategories[T] = WithCategories(value, "DemoFeatures")

  private def tests[T](value: T): WithCategories[T] = WithCategories(value, "TESTCAT")

  private def categories[T](value: T): WithCategories[T] = WithCategories(value, "Category1", "Category2")

  private def all[T](value: T): WithCategories[T] = WithCategories(value, "Category1", "Category2", "DemoFeatures", "TESTCAT")

  private def kafkaConfig(config: Config) = KafkaConfig.parseConfig(config)

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryProvider = createSchemaRegistryProvider(processObjectDependencies)
    Map(
      "sendSms" -> all(new SingleValueSinkFactory(new DiscardingSink)),
      "monitor" -> categories(SinkFactory.noParam(EmptySink)),
      "communicationSink" -> categories(DynamicParametersSink),
      "kafka-string" -> all(new KafkaSinkFactory(new SimpleSerializationSchema[AnyRef](_, String.valueOf), processObjectDependencies)),
      "kafka-avro" -> all(new KafkaAvroSinkFactoryWithEditor(schemaRegistryProvider, processObjectDependencies))
    )
  }

  override def listeners(processObjectDependencies: ProcessObjectDependencies) = List(LoggingListener)

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val schemaRegistryProvider = createSchemaRegistryProvider(processObjectDependencies)
    val avroSourceFactory = new KafkaAvroSourceFactory[Any, Any](schemaRegistryProvider, processObjectDependencies, None)
    Map(
      "real-kafka" -> all(fixedValueKafkaSource[String](
        processObjectDependencies,
        new SimpleStringSchema())
      ),
      "real-kafka-json-SampleProduct" -> all(fixedValueKafkaSource(
        processObjectDependencies,
        new EspDeserializationSchema(bytes => decode[SampleProduct](new String(bytes, StandardCharsets.UTF_8)).right.get)
      )),
      "real-kafka-avro" -> all(avroSourceFactory),
      "kafka-transaction" -> all(FlinkSourceFactory.noParam(new NoEndingSource)),
      "boundedSource" -> categories(BoundedSource),
      "oneSource" -> categories(FlinkSourceFactory.noParam(new OneSource)),
      "communicationSource" -> categories(DynamicParametersSource),
      "csv-source" -> categories(FlinkSourceFactory.noParam(new CsvSource)),
      "genericSourceWithCustomVariables" -> categories(GenericSourceWithCustomVariablesSample),
      "sql-source" -> categories(SqlSource),
      "classInstanceSource" -> all(new ReturningClassInstanceSource)
    )
  }

  private def createSchemaRegistryProvider(processObjectDependencies: ProcessObjectDependencies): SchemaRegistryProvider = {
    val mockConfluent = processObjectDependencies.config.getAs[Boolean](DevProcessConfigCreator.emptyMockedSchemaRegistryProperty).contains(true)
    val confluentFactory: ConfluentSchemaRegistryClientFactory =
      if (mockConfluent) {
        new MockConfluentSchemaRegistryClientFactory(new MockSchemaRegistryClient)
      } else {
        CachedConfluentSchemaRegistryClientFactory()
      }

    ConfluentSchemaRegistryProvider(confluentFactory)
  }

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "accountService" -> categories(EmptyService).withComponentConfig(SingleComponentConfig.zero.copy(docsUrl = Some("accountServiceDocs"))),
    "componentService" -> categories(EmptyService),
    "transactionService" -> categories(EmptyService),
    "serviceModelService" -> categories(EmptyService),
    "paramService" -> categories(OneParamService),
    "enricher" -> categories(Enricher),
    "multipleParamsService" -> categories(MultipleParamsService)
      .withComponentConfig(SingleComponentConfig.zero.copy(
        params = Some(Map(
          "foo" -> ParameterConfig(None, Some(FixedValuesParameterEditor(List(FixedExpressionValue("'test'", "test")))), None, None),
          "bar" -> ParameterConfig(None, Some(StringParameterEditor), None, None),
          "baz" -> ParameterConfig(None, Some(StringParameterEditor), None, None)
        )))
      ),
    "complexReturnObjectService" -> categories(ComplexReturnObjectService),
    "unionReturnObjectService" -> categories(UnionReturnObjectService),
    "listReturnObjectService" -> categories(ListReturnObjectService),
    "clientHttpService" -> categories(new ClientFakeHttpService()),
    "echoEnumService" -> categories(EchoEnumService),
    // types
    "simpleTypesService" -> categories(new SimpleTypesService).withComponentConfig(SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("types")))),
    "optionalTypesService" -> categories(new OptionalTypesService)
      .withComponentConfig(SingleComponentConfig.zero.copy(
        componentGroup = Some(ComponentGroupName("types")),
        params = Some(Map(
          "overriddenByDevConfigParam" -> ParameterConfig(None, None, Some(List(MandatoryParameterValidator)), None),
          "overriddenByFileConfigParam" -> ParameterConfig(None, None, Some(List(MandatoryParameterValidator)), None)
        ))
      )),
    "collectionTypesService" -> categories(new CollectionTypesService).withComponentConfig(SingleComponentConfig.zero.copy(
      componentGroup = Some(ComponentGroupName("types")))),
    "datesTypesService" -> categories(new DatesTypesService).withComponentConfig(SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("types")))),
    "campaignService" -> features(CampaignService),
    "configuratorService" -> features(ConfiguratorService),
    "meetingService" -> features(MeetingService),
    "dynamicService" -> categories(new DynamicService),
    "customValidatedService" -> categories(new CustomValidatedService),
    "modelConfigReader" -> categories(new ModelConfigReaderService(processObjectDependencies.config))
  )

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "noneReturnTypeTransformer" -> tests(NoneReturnTypeTransformer),
    "stateful" -> categories(StatefulTransformer),
    "customFilter" -> categories(CustomFilter),
    "constantStateTransformer" -> categories(ConstantStateTransformer[String](Encoder[ConstantState].apply(ConstantState("stateId", 1234, List("elem1", "elem2", "elem3"))).noSpaces)),
    "constantStateTransformerLongValue" -> categories(ConstantStateTransformer[Long](12333)),
    "additionalVariable" -> categories(AdditionalVariableTransformer),
    "lockStreamTransformer" -> categories(new SampleSignalHandlingTransformer.LockStreamTransformer()),
    "aggregate" -> categories(SlidingAggregateTransformerV2),
    "union" -> categories(UnionTransformer),
    "union-memo" -> categories(UnionWithMemoTransformer),
    "single-side-join" -> categories(SingleSideJoinTransformer),
    "state" -> all(TransformStateTransformer),
    "unionWithEditors" -> all(JoinTransformerWithEditors),
    // types
    "simpleTypesCustomNode" -> categories(new SimpleTypesCustomStreamTransformer).withComponentConfig(SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("types")))),
    "lastVariableWithFilter" -> all(LastVariableFilterTransformer),
    "enrichWithAdditionalData" -> all(EnrichWithAdditionalDataTransformer),
    "sendCommunication" -> all(DynamicParametersTransformer),
    "hideVariables" -> all(HidingVariablesTransformer)
  )

  override def signals(processObjectDependencies: ProcessObjectDependencies) = Map(
    "removeLockSignal" -> all(new RemoveLockProcessSignalFactory(kafkaConfig(processObjectDependencies.config),
      processObjectDependencies.config.getString("signals.topic")))
  )

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ConfigurableExceptionHandlerFactory(processObjectDependencies)

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val globalProcessVariables = Map(
      "AGG" -> categories(new AggregateHelper),
      "DATE" -> all(DateProcessHelper),
      "DICT" -> categories(TestDictionary.instance),
      "RGB" -> all(RGBDictionary.instance),
      "BusinessConfig" -> features(BusinessConfigDictionary.instance),
      "TypedConfig" -> all(ConfigTypedGlobalVariable)
    )

    val additionalClasses = ExpressionConfig.defaultAdditionalClasses ++ List(
      classOf[ReturningTestCaseClass],
      classOf[CronDefinitionBuilder],
      classOf[CronType],
      classOf[CronParser]
    )

    ExpressionConfig(
      globalProcessVariables,
      List.empty,
      additionalClasses,
      LanguageConfiguration(List()),
      dictionaries = Map(
        TestDictionary.id -> categories(TestDictionary.definition),
        RGBDictionary.id -> categories(RGBDictionary.definition),
        BusinessConfigDictionary.id -> features(BusinessConfigDictionary.definition)
      )
    )
  }

  //we generate static generation-time during ConfigCreator creation to test reload mechanisms
  override val buildInfo: Map[String, String] = {
    Map(
      "process-version" -> "0.1",
      "engine-version" -> "0.1",
      "generation-time" -> LocalDateTime.now().toString
    )
  }

  private def fixedValueKafkaSource[T: ClassTag:Encoder:Decoder](processObjectDependencies: ProcessObjectDependencies, schema: DeserializationSchema[T]): KafkaSourceFactory[String, T] = {
    val schemaFactory = new FixedValueDeserializationSchemaFactory(schema)
    val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, T]
    new KafkaSourceFactory[String, T](schemaFactory, None, formatterFactory, processObjectDependencies)
  }
}
