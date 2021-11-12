package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedSingleParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.{BasicContextInitializingFunction, BasicFlinkGenericContextInitializer, FlinkContextInitializer, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

object GenericSourceWithCustomVariablesSample extends SourceFactory[String] with SingleInputGenericNodeTransformation[Source[String]] {

  private class CustomFlinkContextInitializer extends BasicFlinkGenericContextInitializer[String, DefinedParameter] {

    override def validationContext(context: ValidationContext, dependencies: List[NodeDependencyValue], parameters: List[(String, DefinedParameter)])(implicit nodeId: NodeId): ValidationContext = {
      //Append variable "input"
      val contextWithInput = super.validationContext(context, dependencies, parameters)

      //Specify additional variables
      val additionalVariables = Map(
        "additionalOne" -> Typed[String],
        "additionalTwo" -> Typed[Int]
      )

      //Append additional variables to ValidationContext
      additionalVariables.foldLeft(contextWithInput) { case (acc, (name, typingResult)) =>
        acc.withVariable(name, typingResult, None).getOrElse(acc)
      }
    }

    override protected def outputVariableType(context: ValidationContext, dependencies: List[NodeDependencyValue],
                                              parameters: List[(String, DefinedSingleParameter)])
                                             (implicit nodeId: NodeId): typing.TypingResult = Typed[String]

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

  private val customContextInitializer: BasicFlinkGenericContextInitializer[String, DefinedParameter] = new CustomFlinkContextInitializer

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId)
  : GenericSourceWithCustomVariablesSample.NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(Parameter[java.util.List[String]](`elementsParamName`) :: Nil)
    case step@TransformationStep((`elementsParamName`, _) :: Nil, None) =>
      FinalResults(customContextInitializer.validationContext(context, dependencies, step.parameters))
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
