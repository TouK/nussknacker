package pl.touk.nussknacker.engine.flink.api.compat

import org.apache.flink.annotation.{Public, PublicEvolving}
import org.apache.flink.streaming.api.datastream.{DataStreamSink, SingleOutputStreamOperator}
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

  protected def setUidToNodeIdIfNeed[T](nodeCtx: FlinkCustomNodeContext, stream: DataStream[T]): DataStream[T] =
    ExplicitUidInOperatorsCompat.setUidToNodeIdIfNeed(explicitUidInStatefulOperators(nodeCtx), nodeCtx)(stream)

  protected def setUidToNodeIdIfNeed[T](nodeCtx: FlinkCustomNodeContext, stream: DataStreamSink[T]): DataStreamSink[T] =
    ExplicitUidInOperatorsCompat.setUidToNodeIdIfNeedSink(explicitUidInStatefulOperators(nodeCtx), nodeCtx)(stream)

  protected def setUidToNodeIdIfNeed[T](nodeCtx: FlinkCustomNodeContext, stream: SingleOutputStreamOperator[T]): SingleOutputStreamOperator[T] =
    ExplicitUidInOperatorsCompat.setUidToNodeIdIfNeedJava(explicitUidInStatefulOperators(nodeCtx), nodeCtx)(stream)

  /**
   * Rewrite it if you wan to change globally configured behaviour with local one
   */
  @Public
  protected def explicitUidInStatefulOperators(nodeCtx: FlinkCustomNodeContext): Boolean =
    ExplicitUidInOperatorsCompat.defaultExplicitUidInStatefulOperators(nodeCtx)

}

object ExplicitUidInOperatorsCompat {

  def setUidToNodeIdIfNeed[T](explicitUidInStatefulOperators: Boolean, nodeCtx: FlinkCustomNodeContext)
                             (stream: DataStream[T]): DataStream[T] = {
    if (explicitUidInStatefulOperators)
      stream.uid(nodeCtx.nodeId)
    else
      stream
  }

  def setUidToNodeIdIfNeedSink[T](explicitUidInStatefulOperators: Boolean, nodeCtx: FlinkCustomNodeContext)
                                 (stream: DataStreamSink[T]): DataStreamSink[T] = {
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

  // FIXME: replace with config when it be available in FlinkCustomNodeContext
  @PublicEvolving // this field will be switched to true in some future
  def defaultExplicitUidInStatefulOperators(nodeCtx: FlinkCustomNodeContext) = false

}
