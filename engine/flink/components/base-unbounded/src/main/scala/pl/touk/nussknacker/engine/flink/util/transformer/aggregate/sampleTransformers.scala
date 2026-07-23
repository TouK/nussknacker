package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.validation.PositiveDuration

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

object sampleTransformers {

  /**
   * This aggregator can be used for both predefined aggregators (see list below) and for some specialized aggregators like #AGG.map
   * when you switch editor to "raw mode". It also has `emitWhenEventLeft` flag.
   *
   * You should define `#AGG` global variable, because it is used in editors picked for `aggregateBy` parameter.
   */
  object SlidingAggregateTransformerV2 extends CustomStreamTransformer with UnboundedStreamComponent with Serializable {

    private val groupByParameterName = ParameterName("groupBy")

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(
        @ParamName("groupBy") groupBy: LazyParameter[AnyRef],
        @ParamName("aggregator")
        @AdditionalVariables(Array(new AdditionalVariable(name = "AGG", clazz = classOf[AggregateHelper])))
        @Editor(
          `type` = EditorType.FIXED_VALUES_EDITOR,
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
        )
        @Editor(`type` = EditorType.SPEL_EDITOR)
        aggregator: Aggregator,
        @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
        @ParamName("windowLength") @DefaultValue("T(java.time.Duration).parse('PT1H')")
        @HintText(
          "Adding seconds to a long window (e.g. 6 hours 30 seconds) forces second-level state granularity, significantly increasing state size. Use the widest time unit alignment possible (e.g. 6 hours). Must be a positive duration."
        )
        @Editor(
          `type` = EditorType.DURATION_EDITOR,
          timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)
        )
        @Editor(`type` = EditorType.SPEL_EDITOR)
        @PositiveDuration
        length: java.time.Duration,
        @ParamName("emitWhenEventLeft") @DefaultValue("false") emitWhenEventLeft: Boolean,
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId, nodeName: NodeName): ContextTransformation = {
      val windowDuration = Duration(length.toMillis, TimeUnit.MILLISECONDS)
      transformers.slidingTransformer(
        groupBy,
        groupByParameterName,
        aggregateBy,
        aggregator,
        windowDuration,
        variableName,
        emitWhenEventLeft
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
      with Serializable {

    private val groupByParameterName = ParameterName("groupBy")

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(
        @ParamName("groupBy") groupBy: LazyParameter[AnyRef],
        @ParamName("aggregator")
        @AdditionalVariables(Array(new AdditionalVariable(name = "AGG", clazz = classOf[AggregateHelper])))
        @Editor(
          `type` = EditorType.FIXED_VALUES_EDITOR,
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
        )
        @Editor(`type` = EditorType.SPEL_EDITOR)
        aggregator: Aggregator,
        @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
        @ParamName("windowLength") @DefaultValue("T(java.time.Duration).parse('PT1H')")
        @Editor(
          `type` = EditorType.DURATION_EDITOR,
          timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)
        )
        @Editor(`type` = EditorType.SPEL_EDITOR)
        @PositiveDuration
        length: java.time.Duration,
        @ParamName("emitWhen") trigger: TumblingWindowTrigger,
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId, nodeName: NodeName): ContextTransformation = {
      val windowDuration = FiniteDuration(length.toMillis, TimeUnit.MILLISECONDS)
      val maybeOffset = config.tumblingWindowsOffset
        .map(j => FiniteDuration(j.toMillis, TimeUnit.MILLISECONDS))
        .map(o => AggregateWindowsOffsetProvider.offset(windowDuration, o))
      transformers.tumblingTransformer(
        groupBy,
        groupByParameterName,
        aggregateBy,
        aggregator,
        windowDuration,
        variableName,
        trigger,
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
      with Serializable {

    private val groupByParameterName = ParameterName("groupBy")

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(
        @ParamName("groupBy") groupBy: LazyParameter[AnyRef],
        @ParamName("aggregator")
        @AdditionalVariables(Array(new AdditionalVariable(name = "AGG", clazz = classOf[AggregateHelper])))
        @Editor(
          `type` = EditorType.FIXED_VALUES_EDITOR,
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
        )
        @Editor(`type` = EditorType.SPEL_EDITOR)
        aggregator: Aggregator,
        @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
        @ParamName("endSessionCondition") @DefaultValue("false")
        @Editor(`type` = EditorType.SPEL_EDITOR) endSessionCondition: LazyParameter[java.lang.Boolean],
        @ParamName("sessionTimeout") @DefaultValue(
          "T(java.time.Duration).parse('PT1H')"
        )
        @Editor(
          `type` = EditorType.DURATION_EDITOR,
          timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)
        )
        @Editor(`type` = EditorType.SPEL_EDITOR)
        @PositiveDuration
        sessionTimeout: java.time.Duration,
        @ParamName("emitWhen") trigger: SessionWindowTrigger,
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId, nodeName: NodeName): ContextTransformation = {
      val sessionTimeoutDuration = Duration(sessionTimeout.toMillis, TimeUnit.MILLISECONDS)
      transformers.sessionWindowTransformer(
        groupBy,
        groupByParameterName,
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
