package pl.touk.nussknacker.engine.management.sample

import java.time.LocalDateTime

import com.typesafe.config.Config
import io.circe.Encoder
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.SlidingAggregateTransformerV2
import pl.touk.nussknacker.engine.flink.util.transformer.outer.OuterJoinTransformer
import pl.touk.nussknacker.engine.flink.util.transformer.{TransformStateTransformer, UnionTransformer, UnionWithMemoTransformer}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.sink.KafkaSinkFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.management.sample.dict.{BusinessConfigDictionary, RGBDictionary, TestDictionary}
import pl.touk.nussknacker.engine.management.sample.dto.ConstantState
import pl.touk.nussknacker.engine.management.sample.global.ConfigTypedGlobalVariable
import pl.touk.nussknacker.engine.management.sample.helper.DateProcessHelper
import pl.touk.nussknacker.engine.management.sample.service._
import pl.touk.nussknacker.engine.management.sample.signal.{RemoveLockProcessSignalFactory, SampleSignalHandlingTransformer}
import pl.touk.nussknacker.engine.management.sample.source._
import pl.touk.nussknacker.engine.management.sample.transformer._
import pl.touk.nussknacker.engine.util.LoggingListener

object DevProcessConfigCreator {
  val oneElementValue = "One element"
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

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    "sendSms" -> all(SinkFactory.noParam(EmptySink)),
    "monitor" -> categories(SinkFactory.noParam(EmptySink)),
    "communicationSink" -> categories(DynamicParametersSink),
    "kafka-string" -> all(new KafkaSinkFactory(new SimpleSerializationSchema[Any](_, String.valueOf), processObjectDependencies))
  )

  override def listeners(processObjectDependencies: ProcessObjectDependencies) = List(LoggingListener)

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "real-kafka" -> all(new KafkaSourceFactory[String](new SimpleStringSchema,
                                                        None,
                                                        TestParsingUtils.newLineSplit,
                                                        processObjectDependencies)),
    "kafka-transaction" -> all(FlinkSourceFactory.noParam(new NoEndingSource)),
    "boundedSource" -> categories(BoundedSource),
    "oneSource" -> categories(FlinkSourceFactory.noParam(new OneSource)),
    "communicationSource" -> categories(DynamicParametersSource),
    "csv-source" -> categories(FlinkSourceFactory.noParam(new CsvSource))
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "accountService" -> categories(EmptyService).withNodeConfig(SingleNodeConfig.zero.copy(docsUrl = Some("accountServiceDocs"))),
    "componentService" -> categories(EmptyService),
    "transactionService" -> categories(EmptyService),
    "serviceModelService" -> categories(EmptyService),
    "paramService" -> categories(OneParamService),
    "enricher" -> categories(Enricher),
    "multipleParamsService" -> categories(MultipleParamsService)
      .withNodeConfig(SingleNodeConfig.zero.copy(
        params = Some(Map(
          "foo" -> ParameterConfig(None, Some(FixedValuesParameterEditor(List(FixedExpressionValue("test", "test")))), None, None),
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
    "simpleTypesService" -> categories(new SimpleTypesService).withNodeConfig(SingleNodeConfig.zero.copy(category = Some("types"))),
    "optionalTypesService" -> categories(new OptionalTypesService)
      .withNodeConfig(SingleNodeConfig.zero.copy(
        category = Some("types"),
        params = Some(Map(
          "overriddenByDevConfigParam" -> ParameterConfig(None, None, Some(List(MandatoryParameterValidator)), None),
          "overriddenByFileConfigParam" -> ParameterConfig(None, None, Some(List(MandatoryParameterValidator)), None)
        ))
      )),
    "collectionTypesService" -> categories(new CollectionTypesService).withNodeConfig(SingleNodeConfig.zero.copy(
      category = Some("types"))),
    "datesTypesService" -> categories(new DatesTypesService).withNodeConfig(SingleNodeConfig.zero.copy(category = Some("types"))),
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
    "outer-join" -> categories(OuterJoinTransformer),
    "state" -> all(TransformStateTransformer),
    "unionWithEditors" -> all(JoinTransformerWithEditors),
    // types
    "simpleTypesCustomNode" -> categories(new SimpleTypesCustomStreamTransformer).withNodeConfig(SingleNodeConfig.zero.copy(category = Some("types"))),
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
    ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val globalProcessVariables = Map(
      "AGG" -> categories(new AggregateHelper),
      "DATE" -> all(DateProcessHelper),
      "DICT" -> categories(TestDictionary.instance),
      "RGB" -> all(RGBDictionary.instance),
      "BusinessConfig" -> features(BusinessConfigDictionary.instance),
      "TypedConfig" -> all(ConfigTypedGlobalVariable)
    )

    ExpressionConfig(
      globalProcessVariables,
      List.empty,
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
}
