package pl.touk.nussknacker.engine.management.sample.transformer

import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Params, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation

object DynamicParametersTransformer extends CustomStreamTransformer with DynamicParametersMixin {

  override def runComponentLogic(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): AnyRef = {
    // no-op
    FlinkCustomStreamTransformation(_.map(ctx => ValueWithContext[AnyRef](null, ctx)))
  }

}
