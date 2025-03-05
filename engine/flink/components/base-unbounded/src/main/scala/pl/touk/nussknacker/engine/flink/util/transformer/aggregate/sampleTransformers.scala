package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{Component, ProcessingMode, UnboundedStreamComponent}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes.SetOf
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

object sampleTransformers {

  /**
   * This aggregator can be used for both predefined aggregators (see list below) and for some specialized aggregators like #AGG.map
   * when you switch editor to "raw mode". It also has `emitWhenEventLeft` flag.
   *
   * You should define `#AGG` global variable, because it is used in editors picked for `aggregateBy` parameter.
   */
  object SlidingAggregateTransformerV2
      extends CustomStreamTransformer
      with UnboundedStreamComponent
      with ExplicitUidInOperatorsSupport
      with Serializable {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(
        @ParamName("groupBy") groupBy: LazyParameter[CharSequence],
        @ParamName("aggregator")
        @AdditionalVariables(Array(new AdditionalVariable(name = "AGG", clazz = classOf[AggregateHelper])))
        @DualEditor(
          simpleEditor = new SimpleEditor(
            `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
            possibleValues = Array(
              new LabeledExpression(label = "First", expression = "#AGG.first"),
              new LabeledExpression(label = "Last", expression = "#AGG.last"),
              new LabeledExpression(label = "Min", expression = "#AGG.min"),
              new LabeledExpression(label = "Max", expression = "#AGG.max"),
              new LabeledExpression(label = "Sum", expression = "#AGG.sum"),
              new LabeledExpression(label = "Average", expression = "#AGG.average"),
              new LabeledExpression(label = "CountWhen", expression = "#AGG.countWhen"),
              new LabeledExpression(label = "StddevPop", expression = "#AGG.stddevPop"),
              new LabeledExpression(label = "StddevSamp", expression = "#AGG.stddevSamp"),
              new LabeledExpression(label = "VarPop", expression = "#AGG.varPop"),
              new LabeledExpression(label = "VarSamp", expression = "#AGG.varSamp"),
              new LabeledExpression(label = "Median", expression = "#AGG.median"),
              new LabeledExpression(label = "List", expression = "#AGG.list"),
              new LabeledExpression(label = "Set", expression = "#AGG.set"),
              new LabeledExpression(label = "ApproximateSetCardinality", expression = "#AGG.approxCardinality")
            )
          ),
          defaultMode = DualEditorMode.SIMPLE
        ) aggregator: Aggregator,
        @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
        @ParamName("windowLength") @DefaultValue("T(java.time.Duration).parse('PT1H')") length: java.time.Duration,
        @ParamName("emitWhenEventLeft") @DefaultValue("false") emitWhenEventLeft: Boolean,
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length.toMillis, TimeUnit.MILLISECONDS)
      transformers.slidingTransformer(
        groupBy,
        aggregateBy,
        aggregator,
        windowDuration,
        variableName,
        emitWhenEventLeft,
        explicitUidInStatefulOperators
      )
    }

  }

  /**
   * Tumbling window aggregator.
   *
   * You should define `#AGG` global variable, because it is used in editors picked for `aggregateBy` parameter.
   */
  class TumblingAggregateTransformer(config: AggregateWindowsConfig)
      extends CustomStreamTransformer
      with UnboundedStreamComponent
      with ExplicitUidInOperatorsSupport
      with Serializable {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(
        @ParamName("groupBy") groupBy: LazyParameter[CharSequence],
        @ParamName("aggregator")
        @AdditionalVariables(Array(new AdditionalVariable(name = "AGG", clazz = classOf[AggregateHelper])))
        @DualEditor(
          simpleEditor = new SimpleEditor(
            `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
            possibleValues = Array(
              new LabeledExpression(label = "First", expression = "#AGG.first"),
              new LabeledExpression(label = "Last", expression = "#AGG.last"),
              new LabeledExpression(label = "Min", expression = "#AGG.min"),
              new LabeledExpression(label = "Max", expression = "#AGG.max"),
              new LabeledExpression(label = "Sum", expression = "#AGG.sum"),
              new LabeledExpression(label = "Average", expression = "#AGG.average"),
              new LabeledExpression(label = "CountWhen", expression = "#AGG.countWhen"),
              new LabeledExpression(label = "StddevPop", expression = "#AGG.stddevPop"),
              new LabeledExpression(label = "StddevSamp", expression = "#AGG.stddevSamp"),
              new LabeledExpression(label = "VarPop", expression = "#AGG.varPop"),
              new LabeledExpression(label = "VarSamp", expression = "#AGG.varSamp"),
              new LabeledExpression(label = "Median", expression = "#AGG.median"),
              new LabeledExpression(label = "List", expression = "#AGG.list"),
              new LabeledExpression(label = "Set", expression = "#AGG.set"),
              new LabeledExpression(label = "ApproximateSetCardinality", expression = "#AGG.approxCardinality")
            )
          ),
          defaultMode = DualEditorMode.SIMPLE
        ) aggregator: Aggregator,
        @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
        @ParamName("windowLength") @DefaultValue("T(java.time.Duration).parse('PT1H')") length: java.time.Duration,
        @ParamName("emitWhen") trigger: TumblingWindowTrigger,
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length.toMillis, TimeUnit.MILLISECONDS)
      val maybeOffset = config.tumblingWindowsOffset
        .map(j => Duration(j.toMillis, TimeUnit.MILLISECONDS))
        .map(o => AggregateWindowsOffsetProvider.offset(windowDuration, o))
      transformers.tumblingTransformer(
        groupBy,
        aggregateBy,
        aggregator,
        windowDuration,
        variableName,
        trigger,
        explicitUidInStatefulOperators,
        maybeOffset
      )
    }

  }

  /**
   * Session window aggregator. This component is experimental - API may change in the future
   *
   * You should define `#AGG` global variable, because it is used in editors picked for `aggregateBy` parameter.
   */
  object SessionWindowAggregateTransformer
      extends CustomStreamTransformer
      with UnboundedStreamComponent
      with ExplicitUidInOperatorsSupport
      with Serializable {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(
        @ParamName("groupBy") groupBy: LazyParameter[CharSequence],
        @ParamName("aggregator")
        @AdditionalVariables(Array(new AdditionalVariable(name = "AGG", clazz = classOf[AggregateHelper])))
        @DualEditor(
          simpleEditor = new SimpleEditor(
            `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
            possibleValues = Array(
              new LabeledExpression(label = "First", expression = "#AGG.first"),
              new LabeledExpression(label = "Last", expression = "#AGG.last"),
              new LabeledExpression(label = "Min", expression = "#AGG.min"),
              new LabeledExpression(label = "Max", expression = "#AGG.max"),
              new LabeledExpression(label = "Sum", expression = "#AGG.sum"),
              new LabeledExpression(label = "Average", expression = "#AGG.average"),
              new LabeledExpression(label = "CountWhen", expression = "#AGG.countWhen"),
              new LabeledExpression(label = "StddevPop", expression = "#AGG.stddevPop"),
              new LabeledExpression(label = "StddevSamp", expression = "#AGG.stddevSamp"),
              new LabeledExpression(label = "VarPop", expression = "#AGG.varPop"),
              new LabeledExpression(label = "VarSamp", expression = "#AGG.varSamp"),
              new LabeledExpression(label = "Median", expression = "#AGG.median"),
              new LabeledExpression(label = "List", expression = "#AGG.list"),
              new LabeledExpression(label = "Set", expression = "#AGG.set"),
              new LabeledExpression(label = "ApproximateSetCardinality", expression = "#AGG.approxCardinality")
            )
          ),
          defaultMode = DualEditorMode.SIMPLE
        ) aggregator: Aggregator,
        @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
        @ParamName("endSessionCondition") @DefaultValue("false") endSessionCondition: LazyParameter[java.lang.Boolean],
        @ParamName("sessionTimeout") @DefaultValue(
          "T(java.time.Duration).parse('PT1H')"
        ) sessionTimeout: java.time.Duration,
        @ParamName("emitWhen") trigger: SessionWindowTrigger,
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId): ContextTransformation = {
      val sessionTimeoutDuration = Duration(sessionTimeout.toMillis, TimeUnit.MILLISECONDS)
      transformers.sessionWindowTransformer(
        groupBy,
        aggregateBy,
        aggregator,
        sessionTimeoutDuration,
        endSessionCondition,
        trigger,
        variableName
      )
    }

  }

}
