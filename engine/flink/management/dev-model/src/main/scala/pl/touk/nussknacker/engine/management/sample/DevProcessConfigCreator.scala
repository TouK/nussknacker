package pl.touk.nussknacker.engine.management.sample

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import io.circe.parser.decode
import io.circe.{Decoder, Encoder}
import net.ceedubs.ficus.Ficus._
import org.apache.flink.api.common.serialization.{DeserializationSchema, SimpleStringSchema}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.flink.util.sink.{EmptySink, SingleValueSinkFactory}
import pl.touk.nussknacker.engine.flink.util.source.{EspDeserializationSchema, ReturningClassInstanceSource, ReturningTestCaseClass}
import pl.touk.nussknacker.engine.kafka.consumerrecord.{ConsumerRecordToJsonFormatterFactory, FixedValueDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.generic.sinks.FlinkKafkaSinkImplFactory
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.sink.KafkaSinkFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.management.sample.dict.{BusinessConfigDictionary, RGBDictionary, TestDictionary}
import pl.touk.nussknacker.engine.management.sample.dto.{ConstantState, CsvRecord, SampleProduct}
import pl.touk.nussknacker.engine.management.sample.global.{ConfigTypedGlobalVariable, GenericHelperFunction}
import pl.touk.nussknacker.engine.management.sample.helper.DateProcessHelper
import pl.touk.nussknacker.engine.management.sample.service._
import pl.touk.nussknacker.engine.management.sample.source._
import pl.touk.nussknacker.engine.management.sample.transformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{MockSchemaRegistryClientFactory, UniversalSchemaBasedSerdeProvider, UniversalSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.sink.flink.{FlinkKafkaAvroSinkImplFactory, FlinkKafkaUniversalSinkImplFactory}
import pl.touk.nussknacker.engine.schemedkafka.sink.{KafkaAvroSinkFactoryWithEditor, UniversalKafkaSinkFactory}
import pl.touk.nussknacker.engine.schemedkafka.source.{KafkaAvroSourceFactory, UniversalKafkaSourceFactory}
import pl.touk.nussknacker.engine.util.LoggingListener

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
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

  private def userCategories[T](value: T): WithCategories[T] = WithCategories(value, "UserCategory1")

  private def categories[T](value: T): WithCategories[T] = WithCategories(value, "Category1", "Category2")

  private def all[T](value: T): WithCategories[T] = WithCategories(value, "Category1", "Category2", "DemoFeatures", "TESTCAT" , "DevelopmentTests")

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryFactory = createSchemaRegistryClientFactory(processObjectDependencies)
    val avroPayloadSerdeProvider = ConfluentSchemaBasedSerdeProvider.avroPayload(schemaRegistryFactory)
    val universalPayloadSerdeProvider = UniversalSchemaBasedSerdeProvider.create(schemaRegistryFactory)
    Map(
      "sendSms" -> all(new SingleValueSinkFactory(new DiscardingSink)),
      "monitor" -> categories(SinkFactory.noParam(EmptySink)),
      "communicationSink" -> categories(DynamicParametersSink),
      "kafka-string" -> all(new KafkaSinkFactory(new SimpleSerializationSchema[AnyRef](_, String.valueOf), processObjectDependencies, FlinkKafkaSinkImplFactory)),
      "kafka-avro" -> all(new KafkaAvroSinkFactoryWithEditor(schemaRegistryFactory, avroPayloadSerdeProvider, processObjectDependencies, FlinkKafkaAvroSinkImplFactory)),
      "kafka" -> all(new UniversalKafkaSinkFactory(schemaRegistryFactory, universalPayloadSerdeProvider, processObjectDependencies, FlinkKafkaUniversalSinkImplFactory))
    )
  }

  override def listeners(processObjectDependencies: ProcessObjectDependencies) = List(LoggingListener)

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    val schemaRegistryFactory = createSchemaRegistryClientFactory(processObjectDependencies)
    val schemaBasedMessagesSerdeProvider = ConfluentSchemaBasedSerdeProvider.avroPayload(schemaRegistryFactory)
    val universalMessagesSerdeProvider = UniversalSchemaBasedSerdeProvider.create(schemaRegistryFactory)
    val avroSourceFactory = new KafkaAvroSourceFactory[Any, Any](schemaRegistryFactory, schemaBasedMessagesSerdeProvider, processObjectDependencies, new FlinkKafkaSourceImplFactory(None))
    val universalSourceFactory = new UniversalKafkaSourceFactory[Any, Any](schemaRegistryFactory, universalMessagesSerdeProvider, processObjectDependencies, new FlinkKafkaSourceImplFactory(None))
    Map(
      "real-kafka" -> all(fixedValueKafkaSource[String](
        processObjectDependencies,
        new SimpleStringSchema())
      ),
      "real-kafka-json-SampleProduct" -> all(fixedValueKafkaSource(
        processObjectDependencies,
        new EspDeserializationSchema(bytes => decode[SampleProduct](new String(bytes, StandardCharsets.UTF_8)).toOption.get)(TypeInformation.of(classOf[SampleProduct]))
      )),
      "real-kafka-avro" -> all(avroSourceFactory),
      "kafka" -> all(universalSourceFactory),
      "kafka-transaction" -> all(SourceFactory.noParam[String](new NoEndingSource)),
      "boundedSource" -> categories(BoundedSource),
      "oneSource" -> categories(SourceFactory.noParam[String](new OneSource)),
      "communicationSource" -> categories(DynamicParametersSource),
      "csv-source" -> categories(SourceFactory.noParam[CsvRecord](new CsvSource)),
      "genericSourceWithCustomVariables" -> categories(GenericSourceWithCustomVariablesSample),
      "sql-source" -> categories(SqlSource),
      "classInstanceSource" -> all(new ReturningClassInstanceSource)
    )
  }

  private def createSchemaRegistryClientFactory(processObjectDependencies: ProcessObjectDependencies): SchemaRegistryClientFactory = {
    val mockConfluent = processObjectDependencies.config.getAs[Boolean](DevProcessConfigCreator.emptyMockedSchemaRegistryProperty).contains(true)
    if (mockConfluent) {
      MockSchemaRegistryClientFactory.confluentBased(new MockSchemaRegistryClient)
    } else {
      UniversalSchemaRegistryClientFactory
    }
  }

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "accountService" -> categories(EmptyService).withComponentConfig(SingleComponentConfig.zero.copy(docsUrl = Some("accountServiceDocs"))),
    "componentService" -> categories(EmptyService),
    "transactionService" -> categories(EmptyService),
    "serviceModelService" -> categories(EmptyService),
    "userService1" -> userCategories(EmptyService),
    "userService2" -> userCategories(EmptyService),
    "paramService" -> categories(OneParamService),
    "enricher" -> categories(Enricher),
    "enricherNullResult" -> categories(EnricherNullResult),
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
    "constantStateTransformer" -> categories(ConstantStateTransformer[String](Encoder[ConstantState].apply(ConstantState("stateId", 1234, List("elem1", "elem2", "elem3"))).noSpaces)(TypeInformation.of(classOf[String]))),
    "constantStateTransformerLongValue" -> categories(ConstantStateTransformer[Long](12333)(TypeInformation.of(classOf[Long]))),
    "additionalVariable" -> categories(AdditionalVariableTransformer),
    "unionWithEditors" -> all(JoinTransformerWithEditors),
    // types
    "simpleTypesCustomNode" -> categories(new SimpleTypesCustomStreamTransformer).withComponentConfig(SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("types")))),
    "lastVariableWithFilter" -> all(LastVariableFilterTransformer),
    "enrichWithAdditionalData" -> all(EnrichWithAdditionalDataTransformer),
    "sendCommunication" -> all(DynamicParametersTransformer),
    "hideVariables" -> all(HidingVariablesTransformer)
  )

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val globalProcessVariables = Map(
      "DATE" -> all(DateProcessHelper),
      "DICT" -> categories(TestDictionary.instance),
      "RGB" -> all(RGBDictionary.instance),
      "BusinessConfig" -> features(BusinessConfigDictionary.instance),
      "TypedConfig" -> all(ConfigTypedGlobalVariable),
      "HelperFunction" -> all(GenericHelperFunction)
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
    new KafkaSourceFactory[String, T](schemaFactory, formatterFactory, processObjectDependencies, new FlinkKafkaSourceImplFactory(None))
  }
}
