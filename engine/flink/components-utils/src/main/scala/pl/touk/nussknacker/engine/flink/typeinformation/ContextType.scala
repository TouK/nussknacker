package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext

object ContextType {
  def infoBranch(nodeCtx: FlinkCustomNodeContext, key: String): TypeInformation[Context] =
    nodeCtx.typeInformationDetection.forContext(nodeCtx.validationContext.right.get(key))

  def info(nodeCtx: FlinkCustomNodeContext): TypeInformation[Context] =
    nodeCtx.typeInformationDetection.forContext(nodeCtx.validationContext.left.get)
}
