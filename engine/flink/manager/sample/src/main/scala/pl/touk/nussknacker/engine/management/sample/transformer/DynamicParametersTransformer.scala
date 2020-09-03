package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation

object DynamicParametersTransformer extends CustomStreamTransformer with DynamicParametersMixin {

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): AnyRef = {
    //no-op 
    FlinkCustomStreamTransformation(_.map(ctx => ValueWithContext[AnyRef](null, ctx)))
  }

}
