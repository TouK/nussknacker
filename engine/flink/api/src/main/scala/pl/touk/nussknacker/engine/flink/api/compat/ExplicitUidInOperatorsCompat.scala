package pl.touk.nussknacker.engine.flink.api.compat

import org.apache.flink.annotation.{Public, PublicEvolving}
import org.apache.flink.streaming.api.datastream.{DataStreamSink, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
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
    ExplicitUidInOperatorsCompat.setUidIfNeed(explicitUidInStatefulOperators(nodeCtx), nodeCtx.nodeId)(stream)

  protected def setUidToNodeIdIfNeed[T](nodeCtx: FlinkCustomNodeContext, stream: DataStreamSink[T]): DataStreamSink[T] =
    ExplicitUidInOperatorsCompat.setUidIfNeedSink(explicitUidInStatefulOperators(nodeCtx), nodeCtx.nodeId)(stream)

  protected def setUidToNodeIdIfNeed[T](nodeCtx: FlinkCustomNodeContext, stream: SingleOutputStreamOperator[T]): SingleOutputStreamOperator[T] =
    ExplicitUidInOperatorsCompat.setUidIfNeedJava(explicitUidInStatefulOperators(nodeCtx), nodeCtx.nodeId)(stream)

  /**
   * Rewrite it if you wan to change globally configured behaviour with local one
   */
  @Public
  protected def explicitUidInStatefulOperators(nodeCtx: FlinkCustomNodeContext): Boolean =
    ExplicitUidInOperatorsCompat.defaultExplicitUidInStatefulOperators(nodeCtx)

}

object ExplicitUidInOperatorsCompat {

  def setUidIfNeed[T](explicitUidInStatefulOperators: Boolean, uidValue: String)
                     (stream: DataStream[T]): DataStream[T] = {
    if (explicitUidInStatefulOperators)
      stream.uid(uidValue)
    else
      stream
  }

  def setUidIfNeedSink[T](explicitUidInStatefulOperators: Boolean, uidValue: String)
                         (stream: DataStreamSink[T]): DataStreamSink[T] = {
    if (explicitUidInStatefulOperators)
      stream.uid(uidValue)
    else
      stream
  }

  def setUidIfNeedJava[T](explicitUidInStatefulOperators: Boolean, uidValue: String)
                         (stream: SingleOutputStreamOperator[T]): SingleOutputStreamOperator[T] = {
    if (explicitUidInStatefulOperators)
      stream.uid(uidValue)
    else
      stream
  }

  @PublicEvolving // default behaviour will be switched to true in some future
  def defaultExplicitUidInStatefulOperators(nodeCtx: FlinkCustomNodeContext): Boolean =
    defaultExplicitUidInStatefulOperators(nodeCtx.globalParameters)

  @PublicEvolving // default behaviour will be switched to true in some future
  def defaultExplicitUidInStatefulOperators(globalParameters: Option[NkGlobalParameters]): Boolean =
    globalParameters.flatMap(_.configParameters).flatMap(_.explicitUidInStatefulOperators).getOrElse(false)


}
