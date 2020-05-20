package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.context.transformation.GenericNodeTransformation.NodeTransformationDefinition
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedLazyParameter, FailedToDefineParameter, FinalResults, NextParameters, NodeDependencyValue, SingleInputGenericNodeTransformation, WithExplicitMethodToInvokeTransformation}
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

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue]): NodeTransformationDefinition = {
    case Nil => NextParameters(valueParameter::Nil)
    case (`valueParameterName`, DefinedLazyParameter(typ)) :: Nil => NextParameters(conditionParameter(typ)::Nil)
    case (`valueParameterName`, FailedToDefineParameter) :: Nil => NextParameters(conditionParameter(Unknown)::Nil)
    case (`valueParameterName`, _) :: (`conditionParameterName`, _) :: Nil => FinalResults(context)
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
