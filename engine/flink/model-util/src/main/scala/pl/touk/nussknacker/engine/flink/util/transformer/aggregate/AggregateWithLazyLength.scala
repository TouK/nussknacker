package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, LabeledExpression, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, LazyParameter, MethodToInvoke, OutputVariableName, ParamName}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.api.{Context => NkContext, _}
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.flink.util.transformer.richflink._

import scala.collection.immutable.SortedMap

class AggregateWithLazyLength extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

  @MethodToInvoke(returnType = classOf[AnyRef])
  def execute(@ParamName("keyBy") keyBy: LazyParameter[CharSequence],
              @ParamName("aggregator")
              @DualEditor(simpleEditor = new SimpleEditor(
                `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                possibleValues = Array(
                  new LabeledExpression(label = "First", expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).FIRST"),
                  new LabeledExpression(label = "Last",  expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).LAST"),
                  new LabeledExpression(label = "Min",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).MIN"),
                  new LabeledExpression(label = "Max",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).MAX"),
                  new LabeledExpression(label = "Sum",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).SUM"),
                  new LabeledExpression(label = "List",  expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).LIST"),
                  new LabeledExpression(label = "Set",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).SET"),
                  new LabeledExpression(label = "ApproximateSetCardinality", expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).APPROX_CARDINALITY")
                )), defaultMode = DualEditorMode.SIMPLE) aggregator: Aggregator,
              @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
              @ParamName("windowLength") windowLength: LazyParameter[java.time.Duration],
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation
      .definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx

          val aggregatorFunction
              = new AggregatorFunctionWrapper[SortedMap](aggregator, nodeId, aggregateBy.returnType)

          start
            .keyByWithValueX(keyBy, windowLength, _ => aggregateBy)
            .process(aggregatorFunction)
            .setUid(ctx, explicitUidInStatefulOperators)
        })
      )
  }
}
