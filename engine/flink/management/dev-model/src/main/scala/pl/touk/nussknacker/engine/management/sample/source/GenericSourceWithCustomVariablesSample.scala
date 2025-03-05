package pl.touk.nussknacker.engine.management.sample.source

import cats.data.ValidatedNel
import io.circe.Json
import pl.touk.nussknacker.engine.api.{CirceUtil, Context, NodeId, Params}
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter, ParameterDeclaration}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.ContextIdGenerator
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

import scala.jdk.CollectionConverters._

object GenericSourceWithCustomVariablesSample
    extends SourceFactory
    with SingleInputDynamicComponent[Source]
    with UnboundedStreamComponent {

  private class CustomFlinkContextInitializer extends BasicContextInitializer[String](Typed[String]) {

    override def validationContext(
        context: ValidationContext
    )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
      // Append variable "input"
      val contextWithInput = super.validationContext(context)

      // Specify additional variables
      val additionalVariables = Map(
        "additionalOne" -> Typed[String],
        "additionalTwo" -> Typed[Int]
      )

      // Append additional variables to ValidationContext
      additionalVariables.foldLeft(contextWithInput) { case (acc, (name, typingResult)) =>
        acc.andThen(_.withVariable(name, typingResult, None))
      }
    }

    override def initContext(contextIdGenerator: ContextIdGenerator): ContextInitializingFunction[String] =
      new BasicContextInitializingFunction[String](contextIdGenerator, outputVariableName) {

        override def apply(input: String): Context = {
          // perform some transformations and/or computations
          val additionalVariables = Map[String, Any](
            "additionalOne" -> s"transformed:$input",
            "additionalTwo" -> input.length()
          )
          // initialize context with input variable and append computed values
          super.apply(input).withVariables(additionalVariables)
        }

      }

  }

  override type State = Nothing

  // There is only one parameter in this source
  private val elementsParamName = ParameterName("elements")
  private val elementsParamDeclaration =
    ParameterDeclaration.mandatory[java.util.List[String]](elementsParamName).withCreator()

  private val customContextInitializer: ContextInitializer[String] = new CustomFlinkContextInitializer

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): GenericSourceWithCustomVariablesSample.ContextTransformationDefinition = {
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
      timestampAssigner = None,
      returnType = Typed[ProcessingType],
    ) with TestDataGenerator with FlinkSourceTestSupport[ProcessingType] with TestWithParametersSupport[String] {
      override val contextInitializer: ContextInitializer[ProcessingType] = customContextInitializer

      override def generateTestData(size: Int): TestData = TestData(
        (0 to size).flatMap(index => elementsValue.map(el => TestRecord(Json.fromString(el + s"-$index")))).toList
      )

      override def testRecordParser: TestRecordParser[String] = (testRecords: List[TestRecord]) =>
        testRecords.map { testRecord =>
          CirceUtil.decodeJsonUnsafe[String](testRecord.json)
        }

      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[String]] = timestampAssigner

      override def testParametersDefinition: List[Parameter] = elementsParamDeclaration.createParameter() :: Nil

      override def parametersToTestData(params: Map[ParameterName, AnyRef]): String =
        params.getOrElse(elementsParamName, "").toString

    }
  }

  override def nodeDependencies: List[NodeDependency] = Nil

}
