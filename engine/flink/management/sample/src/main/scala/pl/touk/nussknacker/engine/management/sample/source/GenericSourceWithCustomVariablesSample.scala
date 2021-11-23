package pl.touk.nussknacker.engine.management.sample.source

import cats.data.ValidatedNel
import org.apache.flink.streaming.api.scala._
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

object GenericSourceWithCustomVariablesSample extends SourceFactory[String] with SingleInputGenericNodeTransformation[Source] {

  private class CustomFlinkContextInitializer extends BasicContextInitializer[String](Typed[String]) {

    override def validationContext(context: ValidationContext)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
      //Append variable "input"
      val contextWithInput = super.validationContext(context)

      //Specify additional variables
      val additionalVariables = Map(
        "additionalOne" -> Typed[String],
        "additionalTwo" -> Typed[Int]
      )

      //Append additional variables to ValidationContext
      additionalVariables.foldLeft(contextWithInput) { case (acc, (name, typingResult)) =>
        acc.andThen(_.withVariable(name, typingResult, None))
      }
    }

    override def initContext(nodeId: String): ContextInitializingFunction[String] =
      new BasicContextInitializingFunction[String](nodeId, outputVariableName) {
        override def apply(input: String): Context = {
          //perform some transformations and/or computations
          val additionalVariables = Map[String, Any](
            "additionalOne" -> s"transformed:${input}",
            "additionalTwo" -> input.length()
          )
          //initialize context with input variable and append computed values
          super.apply(input).withVariables(additionalVariables)
        }
      }

  }

  override type State = Nothing

  //There is only one parameter in this source
  private val elementsParamName = "elements"

  private val customContextInitializer: ContextInitializer[String] = new CustomFlinkContextInitializer

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId)
  : GenericSourceWithCustomVariablesSample.NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(Parameter[java.util.List[String]](`elementsParamName`) :: Nil)
    case step@TransformationStep((`elementsParamName`, _) :: Nil, None) =>
      FinalResults.forValidation(context)(customContextInitializer.validationContext)
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): Source = {
    import scala.collection.JavaConverters._
    val elements = params(`elementsParamName`).asInstanceOf[java.util.List[String]].asScala.toList

    new CollectionSource[String](StreamExecutionEnvironment.getExecutionEnvironment.getConfig, elements, None, Typed[String])
      with TestDataGenerator
      with FlinkSourceTestSupport[String] {

      override val contextInitializer: ContextInitializer[String] = customContextInitializer

      override def generateTestData(size: Int): Array[Byte] = elements.mkString("\n").getBytes

      override def testDataParser: TestDataParser[String] = new NewLineSplittedTestDataParser[String] {
        override def parseElement(testElement: String): String = testElement
      }

      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[String]] = timestampAssigner
    }
  }

  override def nodeDependencies: List[NodeDependency] = Nil

}
