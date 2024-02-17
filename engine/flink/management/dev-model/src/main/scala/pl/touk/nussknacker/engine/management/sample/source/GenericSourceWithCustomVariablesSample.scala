package pl.touk.nussknacker.engine.management.sample.source

import cats.data.ValidatedNel
import io.circe.Json
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.{CirceUtil, Context, NodeId, Params}
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.ContextIdGenerator
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

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
  private val elementsParamName = "elements"

  private val customContextInitializer: ContextInitializer[String] = new CustomFlinkContextInitializer

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): GenericSourceWithCustomVariablesSample.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(Parameter[java.util.List[String]](`elementsParamName`) :: Nil)
    case TransformationStep((`elementsParamName`, _) :: Nil, None) =>
      FinalResults.forValidation(context)(customContextInitializer.validationContext)
  }

  override def runComponentLogic(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): Source = {
    import scala.jdk.CollectionConverters._
    val elements = params.extractUnsafe[java.util.List[String]](`elementsParamName`).asScala.toList

    new CollectionSource[String](elements, None, Typed[String])(TypeInformation.of(classOf[String]))
      with TestDataGenerator
      with FlinkSourceTestSupport[String] {

      override val contextInitializer: ContextInitializer[String] = customContextInitializer

      override def generateTestData(size: Int): TestData = TestData(elements.map(el => TestRecord(Json.fromString(el))))

      override def testRecordParser: TestRecordParser[String] = (testRecord: TestRecord) =>
        CirceUtil.decodeJsonUnsafe[String](testRecord.json)

      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[String]] = timestampAssigner
    }
  }

  override def nodeDependencies: List[NodeDependency] = Nil

}
