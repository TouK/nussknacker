package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.{Context, ContextInterpreter}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, OutputVariableNameValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

object GenericSourceWithCustomVariablesSample extends FlinkSourceFactory[String] with SingleInputGenericNodeTransformation[Source[String]] {

  override type State = Nothing

  //There is only one parameter in this source
  private val elementsParamName = "elements"

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
    val validatedContextWithInput = context.withVariable(OutputVar.customNode(name), Typed[String])

    //Here two additional variables are appended to ValidationContext.
    val additionalVariables = Map(
      "additionalOne" -> Typed[String],
      "additionalTwo" -> Typed[Int]
    )
    val validatedContextWithInputAndAdditional = additionalVariables.foldLeft(validatedContextWithInput){
      case (acc, (name, typingResult)) => acc.andThen(_.withVariable(name, typingResult, None))
    }
    validatedContextWithInputAndAdditional.getOrElse(context)
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): Source[String] = {
    import scala.collection.JavaConverters._
    val elements = params(`elementsParamName`).asInstanceOf[java.util.List[String]].asScala.toList

    new CollectionSource[String](StreamExecutionEnvironment.getExecutionEnvironment.getConfig, elements, None, Typed[String])
      with TestDataGenerator
      with TestDataParserProvider[String] {

      override def customContextTransformation: Option[Context => Context] = Some({
        ctx => {
          //There is access raw input value...
          val rawInputValue = ctx.get[String](ContextInterpreter.InputVariableName).orNull
          //... and place to perform some additional transformations and/or computations.
          val additionalValues = Map[String, Any](
            "additionalOne" -> s"transformed:${rawInputValue}",
            "additionalTwo" -> rawInputValue.length()
          )
          //The results are appended to the context right after context initialization.
          ctx.withVariables(additionalValues)
        }
      })

      override def generateTestData(size: Int): Array[Byte] = elements.mkString("\n").getBytes

      override def testDataParser: TestDataParser[String] = new NewLineSplittedTestDataParser[String] {
        override def parseElement(testElement: String): String = testElement
      }

    }
  }

  override def nodeDependencies: List[NodeDependency] = Nil

}
