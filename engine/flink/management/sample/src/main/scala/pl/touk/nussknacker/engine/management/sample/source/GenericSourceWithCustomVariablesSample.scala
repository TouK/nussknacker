package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, OutputVariableNameValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkContextInitializer, FlinkContextInitializer, FlinkSourceFactory, FlinkSourceTestSupport, BasicContextInitializingFunction}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

object GenericSourceWithCustomVariablesSample extends FlinkSourceFactory[String] with SingleInputGenericNodeTransformation[Source[String]] {

  private class CustomFlinkContextInitializer extends BasicFlinkContextInitializer[String] {

    override def validationContext(context: ValidationContext, outputVariableName: String, outputVariableType: typing.TypingResult)(implicit nodeId: NodeId): ValidationContext = {
      //Append variable "input"
      val validatedContextWithInput = context.withVariable(OutputVar.customNode(outputVariableName), Typed[String])

      //Specify additional variables
      val additionalVariables = Map(
        "additionalOne" -> Typed[String],
        "additionalTwo" -> Typed[Int]
      )

      //Append additional variables to ValidationContext
      val validatedContextWithInputAndAdditional = additionalVariables.foldLeft(validatedContextWithInput){
        case (acc, (name, typingResult)) => acc.andThen(_.withVariable(name, typingResult, None))
      }
      validatedContextWithInputAndAdditional.getOrElse(context)
    }

    override def initContext(processId: String, taskName: String): MapFunction[String, Context] = {
      new BasicContextInitializingFunction[String](processId, taskName) {
        override def map(input: String): Context = {
          //perform some transformations and/or computations
          val additionalVariables = Map[String, Any](
            "additionalOne" -> s"transformed:${input}",
            "additionalTwo" -> input.length()
          )
          //initialize context with input variable and append computed values
          super.map(input).withVariables(additionalVariables)
        }
      }
    }

  }

  override type State = Nothing

  //There is only one parameter in this source
  private val elementsParamName = "elements"

  private val customContextInitializer: FlinkContextInitializer[String] = new CustomFlinkContextInitializer

  override def initialParameters: List[Parameter] = Parameter[java.util.List[String]](`elementsParamName`)  :: Nil

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId)
  : GenericSourceWithCustomVariablesSample.NodeTransformationDefinition = {
    //The component has simple parameters based only on initialParameters.
    case TransformationStep(Nil, _) => NextParameters(initialParameters)
    case TransformationStep((`elementsParamName`, _)::Nil, None) => FinalResults(finalCtx(context, dependencies))
  }

  private def finalCtx(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): ValidationContext = {
    //Here goes basic declaration of output variable. Append default output variable (name = "input") to ValidationContext.
    val name = dependencies.collectFirst {
      case OutputVariableNameValue(name) => name
    }.get

    customContextInitializer.validationContext(context, name, Typed[String])
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): Source[String] = {
    import scala.collection.JavaConverters._
    val elements = params(`elementsParamName`).asInstanceOf[java.util.List[String]].asScala.toList

    new CollectionSource[String](StreamExecutionEnvironment.getExecutionEnvironment.getConfig, elements, None, Typed[String])
      with TestDataGenerator
      with FlinkSourceTestSupport[String] {

      override val contextInitializer: FlinkContextInitializer[String] = customContextInitializer

      override def generateTestData(size: Int): Array[Byte] = elements.mkString("\n").getBytes

      override def testDataParser: TestDataParser[String] = new NewLineSplittedTestDataParser[String] {
        override def parseElement(testElement: String): String = testElement
      }

      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[String]] = timestampAssigner
    }
  }

  override def nodeDependencies: List[NodeDependency] = Nil

}
