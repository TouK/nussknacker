package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedLazyParameter, FailedToDefineParameter, NodeDependencyValue, SingleInputGenericNodeTransformation, WithExplicitMethodToInvokeTransformation}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}

/* This is example for GenericTransformation, WithExplicitMethodToInvoke will not be here in the future...
   the idea is that we have two parameters:
   - value
   - condition
   And in condition expression we want to have additional variable of type the same as value return type
*/
object LastVariableFilterTransformer extends CustomStreamTransformer with
  WithExplicitMethodToInvokeTransformation with SingleInputGenericNodeTransformation[FlinkCustomStreamTransformation] {

  private val valueParameterName = "value"

  private val conditionParameterName = "condition"

  private val valueParameter = Parameter(valueParameterName, Unknown).copy(isLazyParameter = true)

  private def conditionParameter(previousType: TypingResult) = Parameter(conditionParameterName, Typed[Boolean])
    .copy(isLazyParameter = true, additionalVariables = Map("previous" -> previousType))

  type State = Nothing

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue]): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(valueParameter::Nil)
    case TransformationStep((`valueParameterName`, DefinedLazyParameter(typ)) :: Nil, _) => NextParameters(conditionParameter(typ)::Nil)
    case TransformationStep((`valueParameterName`, FailedToDefineParameter) :: Nil, _) => NextParameters(conditionParameter(Unknown)::Nil)
    case TransformationStep((`valueParameterName`, _) :: (`conditionParameterName`, _) :: Nil, _) => FinalResults(context)
  }

  override def initialParameters: List[Parameter] = List(valueParameter, conditionParameter(Unknown))

  override def nodeDependencies: List[NodeDependency] = Nil

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): AnyRef= {
    val value = params(valueParameterName).asInstanceOf[LazyParameter[Any]]
    val condition = params(conditionParameterName).asInstanceOf[LazyParameter[Any]]

    //we leave implementation as an exercise for the reader :P
    FlinkCustomStreamTransformation((str: DataStream[Context], flinkCustomNodeContext: FlinkCustomNodeContext) => {
      str.map(ValueWithContext[Any](null, _))
    })
  }
  
}
