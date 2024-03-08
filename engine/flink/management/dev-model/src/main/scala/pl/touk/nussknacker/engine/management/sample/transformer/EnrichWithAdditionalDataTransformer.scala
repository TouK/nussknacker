package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.{
  Context,
  CustomStreamTransformer,
  LazyParameter,
  NodeId,
  Params,
  ValueWithContext
}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomJoinTransformation,
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper,
  OneParamLazyParameterFunction
}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

/*
  This is basically left outer join - we join events stream (left side of join) with additional data stream (e.g. users - right side of join)
  Implementation is simplistic, it doesn't wait for additional data stream to initialize etc. - it's mainly to
  show how JoinDynamicComponent works
 */
object EnrichWithAdditionalDataTransformer extends CustomStreamTransformer with JoinDynamicComponent[AnyRef] {

  private val roleValues = List("Events", "Additional data")

  private val role = ParameterWithExtractor.branchMandatory[String](
    name = ParameterName("role"),
    modify = _.copy(
      editor = Some(FixedValuesParameterEditor(roleValues.map(role => FixedExpressionValue(s"'$role'", role))))
    ),
  )

  private val key = ParameterWithExtractor.branchLazyMandatory[String](ParameterName("key"))

  private val additionalDataValueParameterName = ParameterName("additional data value")

  private def additionalDataValue(contexts: Map[String, ValidationContext], byBranch: Map[String, String]) =
    ParameterWithExtractor.lazyMandatory[AnyRef](
      name = additionalDataValueParameterName,
      modify = _.copy(additionalVariables =
        right(byBranch)
          .map(contexts)
          .getOrElse(ValidationContext())
          .localVariables
          .mapValuesNow(AdditionalVariableProvidedInRuntime(_))
      )
    )

  override val canHaveManyInputs: Boolean = true

  override type State = Nothing

  override def contextTransformation(contexts: Map[String, ValidationContext], dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): EnrichWithAdditionalDataTransformer.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(List(role.parameter, key.parameter))
    case TransformationStep(
          (roleParamName, DefinedEagerBranchParameter(byBranch: Map[String, String] @unchecked, _)) ::
          (keyParamName, _) :: Nil,
          _
        ) if roleParamName == role.parameter.name && keyParamName == key.parameter.name =>
      val error =
        if (byBranch.values.toList.sorted != roleValues.sorted)
          List(
            CustomNodeError(
              s"Has to be exactly one Event and Additional data, got: ${byBranch.values.mkString(", ")}",
              Some(role.parameter.name)
            )
          )
        else Nil
      NextParameters(List(additionalDataValue(contexts, byBranch).parameter), error)
    case TransformationStep((roleParamName, FailedToDefineParameter) :: (keyParamName, _) :: Nil, _)
        if roleParamName == role.parameter.name && keyParamName == key.parameter.name =>
      FinalResults(ValidationContext())
    case TransformationStep(
          (roleParamName, DefinedEagerBranchParameter(byBranch: Map[String, String] @unchecked, _)) ::
          (keyParamName, _) ::
          (`additionalDataValueParameterName`, rightValue: DefinedSingleParameter) :: Nil,
          _
        ) if roleParamName == role.parameter.name && keyParamName == key.parameter.name =>
      val outName = OutputVariableNameDependency.extract(dependencies)
      val leftCtx = left(byBranch).map(contexts).getOrElse(ValidationContext())
      FinalResults.forValidation(leftCtx)(_.withVariable(OutputVar.customNode(outName), rightValue.returnType))
  }

  private def left(byBranch: Map[String, String]): Option[String] = byBranch.find(_._2 == "Events").map(_._1)

  private def right(byBranch: Map[String, String]): Option[String] = byBranch.find(_._2 == "Additional data").map(_._1)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): AnyRef = {
    val roleValue = role.extractValue(params)
    val leftName  = left(roleValue)
    val rightName = right(roleValue)
    val keyValue  = key.extractValue(params)
    new FlinkCustomJoinTransformation {
      override def transform(
          inputs: Map[String, DataStream[Context]],
          context: FlinkCustomNodeContext
      ): DataStream[ValueWithContext[AnyRef]] = {
        val leftSide  = inputs(leftName.get)
        val rightSide = inputs(rightName.get)
        leftSide
          .flatMap(context.lazyParameterHelper.lazyMapFunction(keyValue(leftName.get)))
          .connect(rightSide.flatMap(context.lazyParameterHelper.lazyMapFunction(keyValue(rightName.get))))
          .keyBy((v: ValueWithContext[String]) => v.value, (v: ValueWithContext[String]) => v.value)
          .process(
            new EnrichWithAdditionalDataFunction(
              additionalDataValue(Map.empty, Map.empty).extractValue(params),
              context.lazyParameterHelper
            )
          )
      }
    }
  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

}

class EnrichWithAdditionalDataFunction(
    val parameter: LazyParameter[AnyRef],
    val lazyParameterHelper: FlinkLazyParameterFunctionHelper
) extends KeyedCoProcessFunction[String, ValueWithContext[String], ValueWithContext[String], ValueWithContext[AnyRef]]
    with OneParamLazyParameterFunction[AnyRef] {

  private lazy val state =
    getRuntimeContext.getState[AnyRef](new ValueStateDescriptor[AnyRef]("right", classOf[AnyRef]))

  override def processElement1(
      value: ValueWithContext[String],
      ctx: KeyedCoProcessFunction[
        String,
        ValueWithContext[String],
        ValueWithContext[String],
        ValueWithContext[AnyRef]
      ]#Context,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val currentValue = state.value()
    out.collect(ValueWithContext(currentValue, value.context))
  }

  override def processElement2(
      value: ValueWithContext[String],
      ctx: KeyedCoProcessFunction[
        String,
        ValueWithContext[String],
        ValueWithContext[String],
        ValueWithContext[AnyRef]
      ]#Context,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val currentValue = evaluateParameter(value.context)
    state.update(currentValue)
  }

}
