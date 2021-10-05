package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, FlinkCustomNodeContext, FlinkLazyParameterFunctionHelper, OneParamLazyParameterFunction}

/*
  This is basically left outer join - we join events stream (left side of join) with additional data stream (e.g. users - right side of join)
  Implementation is simplistic, it doesn't wait for additional data stream to initialize etc. - it's mainly to
  show how JoinGenericNodeTransformation works
 */
object EnrichWithAdditionalDataTransformer extends CustomStreamTransformer with JoinGenericNodeTransformation[AnyRef] {

  private val roleParameter = "role"

  private val additionalDataValueParameter = "additional data value"

  private val keyParameter = "key"

  private val roleValues = List("Events", "Additional data")

  override def canHaveManyInputs: Boolean = true

  override type State = Nothing

    override def contextTransformation(contexts: Map[String, ValidationContext],
                                       dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): EnrichWithAdditionalDataTransformer.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(List(
        Parameter[String](roleParameter).copy(branchParam = true, editor = Some(FixedValuesParameterEditor(roleValues.map(role => FixedExpressionValue(s"'$role'", role))))),
        Parameter[String](keyParameter).copy(branchParam = true, isLazyParameter = true)))
      case TransformationStep((`roleParameter`, DefinedEagerBranchParameter(byBranch: Map[String, String]@unchecked, _)) :: (`keyParameter`, _) ::Nil, _) =>
        val error = if (byBranch.values.toList.sorted != roleValues.sorted) List(CustomNodeError(s"Has to be exactly one Event and Additional data, got: ${byBranch.values.mkString(", ")}",
          Some(roleParameter))) else Nil
        NextParameters(
          List(Parameter[Any](additionalDataValueParameter).copy(additionalVariables = right(byBranch).map(contexts).getOrElse(ValidationContext()).localVariables, isLazyParameter = true)), error
        )
      case TransformationStep((`roleParameter`, FailedToDefineParameter) :: (`keyParameter`, _) ::Nil, _) =>
        FinalResults(ValidationContext())
      case TransformationStep((`roleParameter`, DefinedEagerBranchParameter(byBranch: Map[String, String]@unchecked, _)) :: (`keyParameter`, _) :: (`additionalDataValueParameter`, rightValue: DefinedSingleParameter) ::Nil, _)
        =>
        val outName = OutputVariableNameDependency.extract(dependencies)
        val leftCtx = left(byBranch).map(contexts).getOrElse(ValidationContext())
        val context = leftCtx.withVariable(OutputVar.customNode(outName), rightValue.returnType)
        FinalResults(context.getOrElse(leftCtx), context.fold(_.toList, _ => Nil))
    }

    private def left(byBranch: Map[String, String]): Option[String] = byBranch.find(_._2 == "Events").map(_._1)

    private def right(byBranch: Map[String, String]): Option[String] = byBranch.find(_._2 == "Additional data").map(_._1)

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): AnyRef = {
      val role = params(roleParameter).asInstanceOf[Map[String, String]]
      val leftName = left(role)
      val rightName = right(role)
      val key = params(keyParameter).asInstanceOf[Map[String, LazyParameter[String]]]
      new FlinkCustomJoinTransformation {
        override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]] = {
          val leftSide = inputs(leftName.get)
          val rightSide = inputs(rightName.get)
          leftSide
            .map(context.lazyParameterHelper.lazyMapFunction(key(leftName.get)))
            .connect(rightSide.map(context.lazyParameterHelper.lazyMapFunction(key(rightName.get))))
            .keyBy(_.value, _.value)
            .process(new EnrichWithAdditionalDataFunction(params(additionalDataValueParameter).asInstanceOf[LazyParameter[AnyRef]], context.lazyParameterHelper))
        }
      }
    }

    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

}

class EnrichWithAdditionalDataFunction(val parameter: LazyParameter[AnyRef], val lazyParameterHelper: FlinkLazyParameterFunctionHelper)
  extends KeyedCoProcessFunction[String, ValueWithContext[String], ValueWithContext[String], ValueWithContext[AnyRef]]
    with OneParamLazyParameterFunction[AnyRef]{

  private lazy val state = getRuntimeContext.getState[AnyRef](new ValueStateDescriptor[AnyRef]("right", classOf[AnyRef]))

  override def processElement1(value: ValueWithContext[String], ctx: KeyedCoProcessFunction[String, ValueWithContext[String],
    ValueWithContext[String], ValueWithContext[AnyRef]]#Context, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val currentValue = state.value()
    out.collect(ValueWithContext(currentValue, value.context))
  }

  override def processElement2(value: ValueWithContext[String], ctx: KeyedCoProcessFunction[String, ValueWithContext[String],
    ValueWithContext[String], ValueWithContext[AnyRef]]#Context, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val currentValue = evaluateParameter(value.context)
    state.update(currentValue)
  }

}