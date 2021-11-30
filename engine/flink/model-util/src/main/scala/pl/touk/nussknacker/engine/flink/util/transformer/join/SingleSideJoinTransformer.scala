package pl.touk.nussknacker.engine.flink.util.transformer.join

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, FlinkCustomNodeContext}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.keyed.{StringKeyOnlyMapper, StringKeyedValue, StringKeyedValueMapper}
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.{AggregateHelper, Aggregator}
import pl.touk.nussknacker.engine.flink.util.transformer.richflink._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.collection.immutable.SortedMap
import scala.concurrent.duration.FiniteDuration

class SingleSideJoinTransformer(timestampAssigner: Option[TimestampWatermarkHandler[TimestampedValue[ValueWithContext[AnyRef]]]])
  extends CustomStreamTransformer with JoinGenericNodeTransformation[FlinkCustomJoinTransformation] with ExplicitUidInOperatorsSupport
    with WithExplicitTypesToExtract with LazyLogging {

  import pl.touk.nussknacker.engine.flink.util.transformer.join.SingleSideJoinTransformer._

  override def canHaveManyInputs: Boolean = true

  override type State = Nothing

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def contextTransformation(contexts: Map[String, ValidationContext], dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(
      List(BranchTypeParam, KeyParam, AggregatorParam, WindowLengthParam).map(_.parameter))
    case TransformationStep(
    (`BranchTypeParamName`, DefinedEagerBranchParameter(branchTypeByBranchId: Map[String, BranchType]@unchecked, _)) ::
    (`KeyParamName`, _) :: (`AggregatorParamName`, _) :: (`WindowLengthParamName`, _) :: Nil, _) =>
      val error = if (branchTypeByBranchId.values.toList.sorted != BranchType.values().toList)
        List(CustomNodeError(s"Has to be exactly one MAIN and JOINED branch, got: ${branchTypeByBranchId.values.mkString(", ")}", Some(BranchTypeParamName)))
      else
        Nil
      val joinedVariables = joinedId(branchTypeByBranchId).map(contexts).getOrElse(ValidationContext())
        .localVariables.mapValuesNow(AdditionalVariableProvidedInRuntime(_))
      NextParameters(List(Parameter[Any](AggregateByParamName).copy(additionalVariables = joinedVariables, isLazyParameter = true)), error)

    case TransformationStep(
    (`BranchTypeParamName`, DefinedEagerBranchParameter(branchTypeByBranchId: Map[String, BranchType]@unchecked, _)) ::
      (`KeyParamName`, _) :: (`AggregatorParamName`, DefinedEagerParameter(aggregator: Aggregator, _)) :: (`WindowLengthParamName`, _) ::
      (`AggregateByParamName`, aggregateBy: DefinedSingleParameter) :: Nil, _) =>
      val outName = OutputVariableNameDependency.extract(dependencies)
      val mainCtx = mainId(branchTypeByBranchId).map(contexts).getOrElse(ValidationContext())
      val validAggregateOutputType = aggregator.computeOutputType(aggregateBy.returnType).leftMap(CustomNodeError(_, Some(AggregatorParamName)))
      FinalResults.forValidation(mainCtx, validAggregateOutputType.swap.toList)(
        _.withVariable(OutputVar.customNode(outName), validAggregateOutputType.getOrElse(Unknown)))
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkCustomJoinTransformation = {
    val branchTypeByBranchId: Map[String, BranchType] = BranchTypeParam.extractValue(params)
    val keyByBranchId: Map[String, LazyParameter[CharSequence]] = KeyParam.extractValue(params)
    val aggregator: Aggregator = AggregatorParam.extractValue(params)
    val window: Duration = WindowLengthParam.extractValue(params)
    val aggregateBy: LazyParameter[AnyRef] = params(AggregateByParamName).asInstanceOf[LazyParameter[AnyRef]]

    new FlinkCustomJoinTransformation with Serializable {
      override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]] = {
        val keyedMainBranchStream = inputs(mainId(branchTypeByBranchId).get)
          .flatMap(new StringKeyOnlyMapper(context.lazyParameterHelper, keyByBranchId(mainId(branchTypeByBranchId).get)))

        val keyedJoinedStream = inputs(joinedId(branchTypeByBranchId).get)
          .flatMap(new StringKeyedValueMapper(context, keyByBranchId(joinedId(branchTypeByBranchId).get), aggregateBy))

        val storedTypeInfo = context.typeInformationDetection.forType(aggregator.computeStoredTypeUnsafe(aggregateBy.returnType))
        val aggregatorFunction = prepareAggregatorFunction(aggregator, FiniteDuration(window.toMillis, TimeUnit.MILLISECONDS), aggregateBy.returnType, storedTypeInfo)(NodeId(context.nodeId))
        val statefulStreamWithUid = keyedMainBranchStream
          .connect(keyedJoinedStream)
          .keyBy(v => v.value, v => v.value.key)
          .process(aggregatorFunction)
          .setUidWithName(context, ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)

        timestampAssigner
          .map(new TimestampAssignmentHelper(_).assignWatermarks(statefulStreamWithUid))
          .getOrElse(statefulStreamWithUid)
      }
    }
  }

  private def mainId(branchTypeByBranchId: Map[String, BranchType]) = {
    branchTypeByBranchId.find(_._2 == BranchType.MAIN).map(_._1)
  }

  private def joinedId(branchTypeByBranchId: Map[String, BranchType]) = {
    branchTypeByBranchId.find(_._2 == BranchType.JOINED).map(_._1)
  }

  protected def prepareAggregatorFunction(aggregator: Aggregator, stateTimeout: FiniteDuration, aggregateElementType: TypingResult, storedTypeInfo: TypeInformation[AnyRef] )
                                         (implicit nodeId: NodeId):
  CoProcessFunction[ValueWithContext[String], ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]] =
    new SingleSideJoinAggregatorFunction[SortedMap](aggregator, stateTimeout.toMillis, nodeId, aggregateElementType, storedTypeInfo)

  override def typesToExtract: List[typing.TypedClass] = List(Typed.typedClass[BranchType])
}

case object SingleSideJoinTransformer extends SingleSideJoinTransformer(None) {

  val BranchTypeParamName = "branchType"
  val BranchTypeParam: ParameterWithExtractor[Map[String, BranchType]] = ParameterWithExtractor.branchMandatory[BranchType](BranchTypeParamName)

  val KeyParamName = "key"
  val KeyParam: ParameterWithExtractor[Map[String, LazyParameter[CharSequence]]] = ParameterWithExtractor.branchLazyMandatory[CharSequence](KeyParamName)

  val AggregatorParamName = "aggregator"
  val AggregatorParam: ParameterWithExtractor[Aggregator] = ParameterWithExtractor
    .mandatory[Aggregator](AggregatorParamName, _.copy(editor = Some(AggregateHelper.DUAL_EDITOR),
      additionalVariables = Map("AGG" -> AdditionalVariableWithFixedValue(new AggregateHelper))))

  val WindowLengthParamName = "windowLength"
  val WindowLengthParam: ParameterWithExtractor[Duration] = ParameterWithExtractor.mandatory[Duration](WindowLengthParamName)

  val AggregateByParamName = "aggregateBy"

}
