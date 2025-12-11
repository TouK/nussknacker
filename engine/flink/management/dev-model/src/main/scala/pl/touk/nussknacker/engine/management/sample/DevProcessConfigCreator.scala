package pl.touk.nussknacker.engine.management.sample

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import io.circe.Encoder
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentConfig, ComponentGroupName, ParameterConfig}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.process.WithCategories.anyCategory
import pl.touk.nussknacker.engine.flink.util.sink.{EmptySink, SingleValueSinkFactory}
import pl.touk.nussknacker.engine.flink.util.source.{ReturningClassInstanceSource, ReturningTestCaseClass}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.management.sample.dict._
import pl.touk.nussknacker.engine.management.sample.dto.ConstantState
import pl.touk.nussknacker.engine.management.sample.global.{ConfigTypedGlobalVariable, GenericHelperFunction}
import pl.touk.nussknacker.engine.management.sample.service._
import pl.touk.nussknacker.engine.management.sample.sink.{DummyKafkaSinkFactory, LiteDeadEndSink}
import pl.touk.nussknacker.engine.management.sample.source._
import pl.touk.nussknacker.engine.management.sample.transformer._
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.util.functions.{
  base64,
  collection,
  conversion,
  date,
  dateFormat,
  geo,
  numeric,
  random,
  util
}

import java.time.LocalDateTime
import java.util.{List => JList}
import scala.annotation.nowarn

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

  @nowarn("cat=deprecation")
  override def sinkFactories(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "sendSms"           -> all(new SingleValueSinkFactory(new DiscardingSink)),
      "monitor"           -> categories(SinkFactory.noParam(EmptySink)),
      "dead-end-lite"     -> categories(SinkFactory.noParam(LiteDeadEndSink)),
      "communicationSink" -> categories(DynamicParametersSink),
      "kafka-string"      -> all(DummyKafkaSinkFactory)
    )
  }

  override def listeners(modelConfig: ModelConfig): Seq[ProcessListener] = List(LoggingListener)

  override def sourceFactories(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[SourceFactory]] = {
    Map(
      "kafka-transaction" -> all(SourceFactory.noParamUnboundedStreamFactory[String](new NoEndingSource(true))),
      "kafka-transaction-no-test-timestamp-assigner" -> all(
        SourceFactory.noParamUnboundedStreamFactory[String](new NoEndingSource(false))
      ),
      "boundedSource"           -> all(BoundedSource),
      "boundedSourceWithOffset" -> all(BoundedSourceWithOffset),
      "oneSource"               -> categories(SourceFactory.noParamUnboundedStreamFactory[String](new OneSource)),
      "communicationSource"     -> categories(DynamicParametersSource),
      "csv-source"      -> categories(SourceFactory.noParamUnboundedStreamFactory[Array[String]](new CsvSource)),
      "csv-source-lite" -> categories(SourceFactory.noParamUnboundedStreamFactory[JList[String]](new LiteCsvSource(_))),
      "genericSourceWithCustomTestingSupport" -> categories(GenericSourceWithCustomTestingSupport),
      "sql-source"                            -> categories(SqlSource),
      "classInstanceSource"                   -> all(new ReturningClassInstanceSource)
    )
  }

  override def services(modelConfig: ModelConfig): Map[String, WithCategories[Service]] =
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
                  editors = Some(List(FixedValuesParameterEditor(List(FixedExpressionValue("'test'", "test"))))),
                  validators = None,
                  label = None,
                  hintText = None,
                  category = None,
                ),
                ParameterName("bar") -> ParameterConfig(
                  None,
                  Some(List(SpelTemplateParameterEditor)),
                  None,
                  None,
                  None,
                  None,
                ),
                ParameterName("baz") -> ParameterConfig(
                  None,
                  Some(List(SpelTemplateParameterEditor)),
                  None,
                  None,
                  None,
                  None,
                )
              )
            )
          )
        ),
      "dynamicMultipleParamsService" -> categories(DynamicMultipleParamsService)
        .withComponentConfig(
          ComponentConfig.zero.copy(
            params = Some(
              Map(
                ParameterName("bar") -> ParameterConfig(
                  defaultValue = Some(Expression.spelTemplate("barValueFromProviderCode")),
                  editors = None,
                  validators = None,
                  label = None,
                  hintText = None,
                  category = None
                )
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
                  editors = None,
                  validators = Some(List(MandatoryParameterValidator)),
                  label = None,
                  hintText = None,
                  category = None,
                ),
                ParameterName("overriddenByFileConfigParam") -> ParameterConfig(
                  defaultValue = None,
                  editors = None,
                  validators = Some(List(MandatoryParameterValidator)),
                  label = None,
                  hintText = None,
                  category = None,
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
      "multipleSelectEditorService"    -> categories(MultiSelectEditorService),
      "hiddenParamTypeService"         -> categories(HiddenParamTypeService),
      "modelConfigReader"              -> categories(new ModelConfigReaderService(modelConfig.underlyingConfig)),
      "log"                            -> all(LoggingService)
    )

  override def customStreamTransformers(
      modelConfig: ModelConfig
  ): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "noneReturnTypeTransformer" -> categories(NoneReturnTypeTransformer),
    "timestampReader"           -> categories(CustomTimestampExtractingTransformationCopy),
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

  override def expressionConfig(modelConfig: ModelConfig): ExpressionConfig = {
    val globalProcessVariables = Map(
      "GEO"            -> anyCategory(geo),
      "NUMERIC"        -> anyCategory(numeric),
      "CONV"           -> anyCategory(conversion),
      "COLLECTION"     -> anyCategory(collection),
      "DATE"           -> anyCategory(date),
      "DATE_FORMAT"    -> anyCategory(dateFormat),
      "UTIL"           -> anyCategory(util),
      "RANDOM"         -> anyCategory(random),
      "BASE64"         -> anyCategory(base64),
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
        IntegerDictionary.id -> IntegerDictionary.definition // not available through global variables, but still available through DictParameterEditor
      )
    )
  }

  // we generate static generation-time during ConfigCreator creation to test reload mechanisms
  override val modelInfo: ModelInfo = {
    ModelInfo.fromMap(
      Map(
        "process-version" -> "0.1",
        "engine-version"  -> "0.1",
        "generation-time" -> LocalDateTime.now().toString
      )
    )
  }

}
