package pl.touk.nussknacker.engine.flink.api.compat

import org.apache.flink.annotation.PublicEvolving
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator
import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext

/**
 * This method is compatibility helper that will prepare for migration to operators with explicitly specified uids.
 * Regards to https://ci.apache.org/projects/flink/flink-docs-stable/ops/upgrading.html#matching-operator-state
 * operator with state should has specified uid. Otherwise recovery of state could fail.
 *
 * In the first step explicitUidInStatefulOperators will be set to false for generic custom transformers.
 * In your own implementations you can use this api and choose if you want to have explicit uids and avoid migration
 * of state in the future, or leave it as it was before (set to false).
 */
trait ExplicitUidInOperatorsCompat {

  protected def explicitUidInStatefulOperators: Boolean

  protected def setUidToNodeIdIfNeed[T](nodeCtx: FlinkCustomNodeContext)(stream: DataStream[T]): DataStream[T] =
    ExplicitUidInOperatorsCompat.setUidToNodeIdIfNeed(explicitUidInStatefulOperators, nodeCtx)(stream)

  protected def setUidToNodeIdIfNeedJava[T](nodeCtx: FlinkCustomNodeContext)(stream: SingleOutputStreamOperator[T]): SingleOutputStreamOperator[T] =
    ExplicitUidInOperatorsCompat.setUidToNodeIdIfNeedJava(explicitUidInStatefulOperators, nodeCtx)(stream)

}

object ExplicitUidInOperatorsCompat {

  def setUidToNodeIdIfNeed[T](explicitUidInStatefulOperators: Boolean, nodeCtx: FlinkCustomNodeContext)
                             (stream: DataStream[T]): DataStream[T] = {
    if (explicitUidInStatefulOperators)
      stream.uid(nodeCtx.nodeId)
    else
      stream
  }
  def setUidToNodeIdIfNeedJava[T](explicitUidInStatefulOperators: Boolean, nodeCtx: FlinkCustomNodeContext)
                                 (stream: SingleOutputStreamOperator[T]): SingleOutputStreamOperator[T] = {
    if (explicitUidInStatefulOperators)
      stream.uid(nodeCtx.nodeId)
    else
      stream
  }

  @PublicEvolving // this field will be switched to true in some future
  val DefaultExplicitUidInStatefulOperators = false

}
