package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.MapFunction
import pl.touk.nussknacker.engine.InterpretationResult
import pl.touk.nussknacker.engine.api.{Context, EndReference}

private[registrar] class EndingNodeInterpretationFunction(nodeId: String) extends MapFunction[Context, InterpretationResult] {

  override def map(ctx: Context): InterpretationResult = {
    InterpretationResult(EndReference(nodeId), null, ctx)
  }

}
