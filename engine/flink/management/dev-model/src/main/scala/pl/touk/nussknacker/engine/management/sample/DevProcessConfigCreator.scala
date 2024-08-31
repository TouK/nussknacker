package pl.touk.nussknacker.engine.management.sample

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import io.circe.parser.decode
import io.circe.{Decoder, Encoder}
import org.apache.flink.api.common.serialization.{DeserializationSchema, SimpleStringSchema}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentConfig, ComponentGroupName, ParameterConfig}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.flink.util.sink.{EmptySink, SingleValueSinkFactory}
import pl.touk.nussknacker.engine.flink.util.source.{
  EspDeserializationSchema,
  ReturningClassInstanceSource,
  ReturningTestCaseClass
}
import pl.touk.nussknacker.engine.kafka.consumerrecord.{
  ConsumerRecordToJsonFormatterFactory,
  FixedValueDeserializationSchemaFactory
}
import pl.touk.nussknacker.engine.kafka.generic.sinks.FlinkKafkaSinkImplFactory
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.sink.KafkaSinkFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.management.sample.dict._
import pl.touk.nussknacker.engine.management.sample.dto.{ConstantState, CsvRecord, SampleProduct}
import pl.touk.nussknacker.engine.management.sample.global.{ConfigTypedGlobalVariable, GenericHelperFunction}
import pl.touk.nussknacker.engine.management.sample.helper.DateProcessHelper
import pl.touk.nussknacker.engine.management.sample.service._
import pl.touk.nussknacker.engine.management.sample.sink.LiteDeadEndSink
import pl.touk.nussknacker.engine.management.sample.source._
import pl.touk.nussknacker.engine.management.sample.transformer._
import pl.touk.nussknacker.engine.util.LoggingListener

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import scala.reflect.ClassTag

object DevProcessConfigCreator {
  val oneElementValue = "One element"
}

/**
  * This config creator is for purpose of development, for end-to-end tests
  */
class DevProcessConfigCreator extends ProcessConfigCreator {

  private def userCategories[T](value: T): WithCategories[T] = WithCategories(value, "UserCategory1")

  private def categories[T](value: T): WithCategories[T] = WithCategories(value, "Category1", "Category2")

  private def all[T](value: T): WithCategories[T] =
    WithCategories(value, "Category1", "Category2", "DevelopmentTests", "Periodic")

  override def sinkFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "sendSms"           -> all(new SingleValueSinkFactory(new DiscardingSink)),
      "monitor"           -> categories(SinkFactory.noParam(EmptySink)),
      "dead-end-lite"     -> categories(SinkFactory.noParam(LiteDeadEndSink)),
      "communicationSink" -> categories(DynamicParametersSink),
      "kafka-string" -> all(
        new KafkaSinkFactory(
          new SimpleSerializationSchema[AnyRef](_, String.valueOf),
          modelDependencies,
          FlinkKafkaSinkImplFactory
        )
      )
    )
  }

  override def listeners(modelDependencies: ProcessObjectDependencies): Seq[ProcessListener] = List(LoggingListener)

  override def sourceFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = {
    Map(
      "real-kafka" -> all(fixedValueKafkaSource[String](modelDependencies, new SimpleStringSchema())),
      "real-kafka-json-SampleProduct" -> all(
        fixedValueKafkaSource(
          modelDependencies,
          new EspDeserializationSchema(bytes =>
            decode[SampleProduct](new String(bytes, StandardCharsets.UTF_8)).toOption.get
          )(TypeInformation.of(classOf[SampleProduct]))
        )
      ),
      "kafka-transaction"   -> all(SourceFactory.noParamUnboundedStreamFactory[String](new NoEndingSource)),
      "boundedSource"       -> all(BoundedSource),
      "oneSource"           -> categories(SourceFactory.noParamUnboundedStreamFactory[String](new OneSource)),
      "communicationSource" -> categories(DynamicParametersSource),
      "csv-source"          -> categories(SourceFactory.noParamUnboundedStreamFactory[CsvRecord](new CsvSource)),
      "csv-source-lite"     -> categories(SourceFactory.noParamUnboundedStreamFactory[CsvRecord](new LiteCsvSource(_))),
      "genericSourceWithCustomVariables" -> categories(GenericSourceWithCustomVariablesSample),
      "sql-source"                       -> categories(SqlSource),
      "classInstanceSource"              -> all(new ReturningClassInstanceSource)
    )
  }

  override def services(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
    Map(
      "accountService" -> categories(EmptyService).withComponentConfig(
        ComponentConfig.zero.copy(docsUrl = Some("accountServiceDocs"))
      ),
      "componentService"    -> categories(EmptyService),
      "transactionService"  -> categories(EmptyService),
      "serviceModelService" -> categories(EmptyService),
      "userService1"        -> userCategories(EmptyService),
      "userService2"        -> userCategories(EmptyService),
      "paramService"        -> categories(OneParamService),
      "enricher"            -> categories(Enricher),
      "enricherNullResult"  -> categories(EnricherNullResult),
      "multipleParamsService" -> categories(MultipleParamsService)
        .withComponentConfig(
          ComponentConfig.zero.copy(
            params = Some(
              Map(
                ParameterName("foo") -> ParameterConfig(
                  defaultValue = None,
                  editor = Some(FixedValuesParameterEditor(List(FixedExpressionValue("'test'", "test")))),
                  validators = None,
                  label = None,
                  hintText = None
                ),
                ParameterName("bar") -> ParameterConfig(None, Some(StringParameterEditor), None, None, None),
                ParameterName("baz") -> ParameterConfig(None, Some(StringParameterEditor), None, None, None)
              )
            )
          )
        ),
      "dynamicMultipleParamsService" -> categories(DynamicMultipleParamsService)
        .withComponentConfig(
          ComponentConfig.zero.copy(
            params = Some(
              Map(
                ParameterName("bar") -> ParameterConfig(Some("'barValueFromProviderCode'"), None, None, None, None)
              )
            )
          )
        ),
      "complexReturnObjectService" -> categories(ComplexReturnObjectService),
      "unionReturnObjectService"   -> categories(UnionReturnObjectService),
      "listReturnObjectService"    -> categories(ListReturnObjectService),
      "clientHttpService"          -> categories(new ClientFakeHttpService()),
      "echoEnumService"            -> categories(EchoEnumService),
      // types
      "simpleTypesService" -> categories(new SimpleTypesService)
        .withComponentConfig(ComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("types")))),
      "optionalTypesService" -> categories(new OptionalTypesService)
        .withComponentConfig(
          ComponentConfig.zero.copy(
            componentGroup = Some(ComponentGroupName("types")),
            params = Some(
              Map(
                ParameterName("overriddenByDevConfigParam") -> ParameterConfig(
                  defaultValue = None,
                  editor = None,
                  validators = Some(List(MandatoryParameterValidator)),
                  label = None,
                  hintText = None
                ),
                ParameterName("overriddenByFileConfigParam") -> ParameterConfig(
                  defaultValue = None,
                  editor = None,
                  validators = Some(List(MandatoryParameterValidator)),
                  label = None,
                  hintText = None
                )
              )
            )
          )
        ),
      "collectionTypesService" -> categories(new CollectionTypesService)
        .withComponentConfig(ComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("types")))),
      "datesTypesService" -> categories(new DatesTypesService)
        .withComponentConfig(ComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("types")))),
      "campaignService"                -> WithCategories(CampaignService, "Category2"),
      "configuratorService"            -> categories(ConfiguratorService),
      "meetingService"                 -> categories(MeetingService),
      "dynamicService"                 -> categories(new DynamicService),
      "customValidatedService"         -> categories(new CustomValidatedService),
      "serviceWithDictParameterEditor" -> categories(new ServiceWithDictParameterEditor),
      "modelConfigReader"              -> categories(new ModelConfigReaderService(modelDependencies.config)),
      "log"                            -> all(LoggingService)
    )

  override def customStreamTransformers(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "noneReturnTypeTransformer" -> categories(NoneReturnTypeTransformer),
    "stateful"                  -> categories(StatefulTransformer),
    "customFilter"              -> categories(CustomFilter),
    "constantStateTransformer" -> categories(
      ConstantStateTransformer[String](
        Encoder[ConstantState].apply(ConstantState("stateId", 1234, List("elem1", "elem2", "elem3"))).noSpaces
      )(TypeInformation.of(classOf[String]))
    ),
    "constantStateTransformerLongValue" -> categories(
      ConstantStateTransformer[Long](12333)(TypeInformation.of(classOf[Long]))
    ),
    "additionalVariable" -> categories(AdditionalVariableTransformer),
    "unionWithEditors"   -> all(JoinTransformerWithEditors),
    // types
    "simpleTypesCustomNode" -> categories(new SimpleTypesCustomStreamTransformer)
      .withComponentConfig(ComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("types")))),
    "lastVariableWithFilter"   -> all(LastVariableFilterTransformer),
    "enrichWithAdditionalData" -> all(EnrichWithAdditionalDataTransformer),
    "sendCommunication"        -> all(DynamicParametersTransformer),
    "hideVariables"            -> all(HidingVariablesTransformer)
  )

  override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val globalProcessVariables = Map(
      "DATE"           -> all(DateProcessHelper),
      "DICT"           -> categories(TestDictionary.instance),
      "RGB"            -> all(RGBDictionary.instance),
      "BusinessConfig" -> categories(BusinessConfigDictionary.instance),
      "TypedConfig"    -> all(ConfigTypedGlobalVariable),
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
      dictionaries = Map(
        TestDictionary.id           -> TestDictionary.definition,
        RGBDictionary.id            -> RGBDictionary.definition,
        BusinessConfigDictionary.id -> BusinessConfigDictionary.definition,
        BooleanDictionary.id -> BooleanDictionary.definition, // not available through global variables, but still available through DictParameterEditor
        LongDictionary.id -> LongDictionary.definition, // not available through global variables, but still available through DictParameterEditor
      )
    )
  }

  // we generate static generation-time during ConfigCreator creation to test reload mechanisms
  override val buildInfo: Map[String, String] = {
    Map(
      "process-version" -> "0.1",
      "engine-version"  -> "0.1",
      "generation-time" -> LocalDateTime.now().toString
    )
  }

  private def fixedValueKafkaSource[T: ClassTag: Encoder: Decoder](
      modelDependencies: ProcessObjectDependencies,
      schema: DeserializationSchema[T]
  ): KafkaSourceFactory[String, T] = {
    val schemaFactory    = new FixedValueDeserializationSchemaFactory(schema)
    val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, T]
    new KafkaSourceFactory[String, T](
      schemaFactory,
      formatterFactory,
      modelDependencies,
      new FlinkKafkaSourceImplFactory(None)
    )
  }

}
