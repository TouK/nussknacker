package pl.touk.nussknacker.engine.flink.util.transformer.join

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, FlinkCustomNodeContext}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.typeinformation.KeyedValueType
import pl.touk.nussknacker.engine.flink.util.keyed
import pl.touk.nussknacker.engine.flink.util.keyed.{StringKeyOnlyMapper, StringKeyedValue, StringKeyedValueMapper}
import pl.touk.nussknacker.engine.flink.util.richflink._
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.{AggregateHelper, Aggregator}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.KeyedValue

import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.collection.immutable.SortedMap
import scala.concurrent.duration.FiniteDuration

class SingleSideJoinTransformer(
    timestampAssigner: Option[TimestampWatermarkHandler[TimestampedValue[ValueWithContext[AnyRef]]]]
) extends CustomStreamTransformer
    with JoinDynamicComponent[FlinkCustomJoinTransformation]
    with ExplicitUidInOperatorsSupport
    with WithExplicitTypesToExtract
    with LazyLogging
    with Serializable {

  import pl.touk.nussknacker.engine.flink.util.transformer.join.SingleSideJoinTransformer._

  override type State = Nothing

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def contextTransformation(contexts: Map[String, ValidationContext], dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        List(BranchTypeParamDeclaration, KeyParamDeclaration, AggregatorParamDeclaration, WindowLengthParamDeclaration)
          .map(_.createParameter())
      )
    case TransformationStep(
          (
            `BranchTypeParamName`,
            DefinedEagerBranchParameter(branchTypeByBranchId: Map[String, BranchType] @unchecked, _)
          ) ::
          (`KeyParamName`, _) :: (`AggregatorParamName`, _) :: (`WindowLengthParamName`, _) :: Nil,
          _
        ) =>
      val error =
        if (branchTypeByBranchId.values.toList.sorted != BranchType.values().toList)
          List(
            CustomNodeError(
              s"Has to be exactly one MAIN and JOINED branch, got: ${branchTypeByBranchId.values.mkString(", ")}",
              Some(BranchTypeParamName)
            )
          )
        else
          Nil
      val joinedVariables = joinedId(branchTypeByBranchId)
        .map(contexts)
        .getOrElse(ValidationContext())
        .localVariables
        .mapValuesNow(AdditionalVariableProvidedInRuntime(_))
      NextParameters(
        List(Parameter[Any](AggregateByParamName).copy(additionalVariables = joinedVariables, isLazyParameter = true)),
        error
      )

    case TransformationStep(
          (
            `BranchTypeParamName`,
            DefinedEagerBranchParameter(branchTypeByBranchId: Map[String, BranchType] @unchecked, _)
          ) ::
          (`KeyParamName`, _) :: (`AggregatorParamName`, DefinedEagerParameter(aggregator: Aggregator, _)) :: (
            `WindowLengthParamName`,
            _
          ) ::
          (`AggregateByParamName`, aggregateBy: DefinedSingleParameter) :: Nil,
          _
        ) =>
      val outName = OutputVariableNameDependency.extract(dependencies)
      val mainCtx = mainId(branchTypeByBranchId).map(contexts).getOrElse(ValidationContext())
      val validAggregateOutputType =
        aggregator.computeOutputType(aggregateBy.returnType).leftMap(CustomNodeError(_, Some(AggregatorParamName)))
      FinalResults.forValidation(mainCtx, validAggregateOutputType.swap.toList)(
        _.withVariable(OutputVar.customNode(outName), validAggregateOutputType.getOrElse(Unknown))
      )
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): FlinkCustomJoinTransformation = {
    val branchTypeByBranchId: Map[String, BranchType]           = BranchTypeParamDeclaration.extractValueUnsafe(params)
    val keyByBranchId: Map[String, LazyParameter[CharSequence]] = KeyParamDeclaration.extractValueUnsafe(params)
    val aggregator: Aggregator                                  = AggregatorParamDeclaration.extractValueUnsafe(params)
    val window: Duration                   = WindowLengthParamDeclaration.extractValueUnsafe(params)
    val aggregateBy: LazyParameter[AnyRef] = params.extractUnsafe[LazyParameter[AnyRef]](AggregateByParamName)
    val outputType                         = aggregator.computeOutputTypeUnsafe(aggregateBy.returnType)

    new FlinkCustomJoinTransformation with Serializable {
      override def transform(
          inputs: Map[String, DataStream[Context]],
          context: FlinkCustomNodeContext
      ): DataStream[ValueWithContext[AnyRef]] = {
        val mainBranchId = mainId(branchTypeByBranchId).get

        val keyedMainBranchStream = inputs(mainBranchId)
          .flatMap(
            new StringKeyOnlyMapper(context.lazyParameterHelper, keyByBranchId(mainBranchId)),
            context.valueWithContextInfo.forBranch[String](mainBranchId, Typed.typedClass[String])
          )

        val joinedBranchId = joinedId(branchTypeByBranchId).get

        val joinedTypeInfo: TypeInformation[ValueWithContext[KeyedValue[String, AnyRef]]] =
          context.valueWithContextInfo.forBranch(
            joinedBranchId,
            KeyedValueType.info(TypeInformationDetection.instance.forType[AnyRef](aggregateBy.returnType))
          )

        val keyedJoinedStream = inputs(joinedBranchId)
          .flatMap(
            new StringKeyedValueMapper(context, keyByBranchId(joinedBranchId), aggregateBy),
            joinedTypeInfo
          )

        val storedTypeInfo = TypeInformationDetection.instance
          .forType[AnyRef](aggregator.computeStoredTypeUnsafe(aggregateBy.returnType))

        val aggregatorFunction = prepareAggregatorFunction(
          aggregator,
          FiniteDuration(window.toMillis, TimeUnit.MILLISECONDS),
          aggregateBy.returnType,
          storedTypeInfo,
          context.convertToEngineRuntimeContext
        )(NodeId(context.nodeId))

        val statefulStreamWithUid = keyedMainBranchStream
          .connect(keyedJoinedStream)
          .keyBy(
            (v: ValueWithContext[String]) => v.value,
            (v: ValueWithContext[StringKeyedValue[AnyRef]]) => v.value.key
          )
          // TODO: Add TypeInfo, it's probably outputType from AggregatorFunctionMixin?
          .process(aggregatorFunction)
          .setUidWithName(context, ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)

        timestampAssigner
          .map(
            new TimestampAssignmentHelper(_)(context.valueWithContextInfo.forType[AnyRef](outputType))
              .assignWatermarks(statefulStreamWithUid)
          )
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

  protected def prepareAggregatorFunction(
      aggregator: Aggregator,
      stateTimeout: FiniteDuration,
      aggregateElementType: TypingResult,
      storedTypeInfo: TypeInformation[AnyRef],
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
  )(
      implicit nodeId: NodeId
  ): CoProcessFunction[ValueWithContext[String], ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]] =
    new SingleSideJoinAggregatorFunction[SortedMap](
      aggregator,
      stateTimeout.toMillis,
      nodeId,
      aggregateElementType,
      storedTypeInfo,
      convertToEngineRuntimeContext
    )

  override def typesToExtract: List[typing.TypedClass] = List(
    Typed.typedClass[BranchType],
    Typed.typedClass[AggregateHelper]
  )

}

case object SingleSideJoinTransformer extends SingleSideJoinTransformer(None) with UnboundedStreamComponent {

  val BranchTypeParamName: ParameterName = ParameterName("branchType")

  val BranchTypeParamDeclaration: ParameterCreatorWithNoDependency with ParameterExtractor[Map[String, BranchType]] =
    ParameterDeclaration.branchMandatory[BranchType](BranchTypeParamName).withCreator()

  val KeyParamName: ParameterName = ParameterName("key")

  val KeyParamDeclaration
      : ParameterCreatorWithNoDependency with ParameterExtractor[Map[String, LazyParameter[CharSequence]]] =
    ParameterDeclaration.branchLazyMandatory[CharSequence](KeyParamName).withCreator()

  val AggregatorParamName: ParameterName = ParameterName("aggregator")

  val AggregatorParamDeclaration: ParameterCreatorWithNoDependency with ParameterExtractor[Aggregator] =
    ParameterDeclaration
      .mandatory[Aggregator](AggregatorParamName)
      .withCreator(
        modify = _.copy(
          editor = Some(AggregateHelper.DUAL_EDITOR),
          additionalVariables = Map("AGG" -> AdditionalVariableWithFixedValue(new AggregateHelper))
        )
      )

  val WindowLengthParamName: ParameterName = ParameterName("windowLength")
  val WindowLengthParamDeclaration: ParameterCreatorWithNoDependency with ParameterExtractor[Duration] =
    ParameterDeclaration.mandatory[Duration](WindowLengthParamName).withCreator()

  val AggregateByParamName: ParameterName = ParameterName("aggregateBy")

}
