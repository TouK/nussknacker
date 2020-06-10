package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MetaData, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation
import pl.touk.nussknacker.engine.util.Implicits._

object DynamicParametersTransformer extends CustomStreamTransformer with SingleInputGenericNodeTransformation[AnyRef] {

  override type State = Nothing

  private val choiceParamName = "communicationType"

  private val choiceParam = Parameter[String](choiceParamName).copy(
    editor = Some(FixedValuesParameterEditor(List(
      FixedExpressionValue("'SMS'", "sms"),
      FixedExpressionValue("'MAIL'", "mail")
    )))
  )

  private val paramsMap = Map(
    "SMS" -> List(Parameter[String]("Number"), Parameter[String]("Text")),
    "MAIL" -> List(Parameter[String]("Address"), Parameter[String]("Subject"), Parameter[String]("Text"))
  )

  override def contextTransformation(context: ValidationContext,
                                     dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): this.NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(initialParameters)
    case TransformationStep((`choiceParamName`, DefinedEagerParameter(value: String, _)) :: Nil, None) =>
      paramsMap.get(value).map(NextParameters(_)).getOrElse(NextParameters(Nil, List(CustomNodeError(s"Unknown type: ${value}", Some(choiceParamName)))))
    case TransformationStep((`choiceParamName`, FailedToDefineParameter) :: Nil, None) =>
      FinalResults(context)
    case TransformationStep((`choiceParamName`, _) :: otherParams, None)  =>
      FinalResults(context)
  }

  override def initialParameters: List[Parameter] = List(choiceParam)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): AnyRef = {
    //no-op 
    FlinkCustomStreamTransformation(_.map(ctx => ValueWithContext[Any](null, ctx)))
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]))

}
