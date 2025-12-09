package pl.touk.nussknacker.engine.management.sample.source

import io.circe.Json
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import pl.touk.nussknacker.engine.api.{CirceUtil, NodeId, Params, VariableConstants}
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter, ParameterDeclaration}
import pl.touk.nussknacker.engine.api.livedata.{DataRecord, DataRecords, LiveDataProvider}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

import java.time.Instant
import scala.jdk.CollectionConverters._

object GenericSourceWithCustomTestingSupport
    extends SourceFactory
    with SingleInputDynamicComponent
    with UnboundedStreamComponent {

  override type Implementation = Source

  override type State = Nothing

  // There is only one parameter in this source
  private val elementsParamName = ParameterName("elements")
  private val elementsParamDeclaration =
    ParameterDeclaration.mandatory[java.util.List[String]](elementsParamName).withCreator()

  private val customContextInitializer: ContextInitializer[String] = new BasicContextInitializer[String](Typed[String])

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(elementsParamDeclaration.createParameter() :: Nil)
    case TransformationStep((`elementsParamName`, _) :: Nil, None) =>
      FinalResults.forValidation(context)(customContextInitializer.validationContext)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): Source = {
    val elementsValue = elementsParamDeclaration.extractValueUnsafe(params).asScala.toList

    new CollectionSource[ProcessingType](
      list = elementsValue,
      watermarkStrategy = None,
      returnType = Typed[ProcessingType],
    ) with TestDataGenerator
      with FlinkSourceTestSupport[ProcessingType]
      with TestWithParametersSupport[ProcessingType]
      with LiveDataProvider {

      override val contextInitializer: ContextInitializer[ProcessingType] = customContextInitializer

      override def fetchLiveData(maxNumberOfRecords: Int): DataRecords = DataRecords(
        (0 until maxNumberOfRecords).flatMap { index =>
          elementsValue.map { el =>
            DataRecord(
              variables = Map(VariableConstants.InputVariableName -> (el + s"-$index")),
              upstreamTimestamp = Some(Instant.ofEpochMilli(123))
            )
          }
        }.toList
      )

      override def generateTestData(size: Int): TestData = TestData(
        (0 until size).flatMap(index => elementsValue.map(el => TestRecord(Json.fromString(el + s"-$index")))).toList
      )

      override def testRecordParser: TestRecordParser[String] = (testRecords: List[TestRecord]) =>
        testRecords.map { testRecord =>
          CirceUtil.decodeJsonUnsafe[String](testRecord.json)
        }

      override def watermarkStrategyForTest: Option[WatermarkStrategy[String]] = watermarkStrategy

      override def testParametersDefinition: List[Parameter] = elementsParamDeclaration.createParameter() :: Nil

      override def parametersToTestData(params: Map[ParameterName, AnyRef]): String =
        params.getOrElse(elementsParamName, "").toString

    }
  }

  override def nodeDependencies: List[NodeDependency] = Nil

}
