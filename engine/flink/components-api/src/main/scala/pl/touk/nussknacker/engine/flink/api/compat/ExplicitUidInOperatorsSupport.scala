package pl.touk.nussknacker.engine.flink.api.compat

import org.apache.flink.annotation.{Public, PublicEvolving}
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink, SingleOutputStreamOperator}
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext

/**
 * This trait helps to explicitly set uids for Flink's stateful operators.
 * Regards to https://ci.apache.org/projects/flink/flink-docs-stable/ops/upgrading.html#matching-operator-state
 * operator with state should has specified uid. Otherwise recovery of state could fail.
 *
 * Currently this class sets up uids by default
 *
 * You can disable this globally by adding:
 * ```
 * globalParameters {
 *   explicitUidInStatefulOperators: false
 * }
 * ```
 * in your model config.
 *
 */
trait ExplicitUidInOperatorsSupport {

  protected def setUidToNodeIdIfNeed[T](nodeCtx: FlinkCustomNodeContext, stream: DataStream[T]): DataStream[T] =
    ExplicitUidInOperatorsSupport.setUidIfNeed(explicitUidInStatefulOperators(nodeCtx), nodeCtx.nodeId)(stream)

  protected def setUidToNodeIdIfNeed[T](nodeCtx: FlinkCustomNodeContext, stream: DataStreamSink[T]): DataStreamSink[T] =
    ExplicitUidInOperatorsSupport.setUidIfNeedSink(explicitUidInStatefulOperators(nodeCtx), nodeCtx.nodeId)(stream)

  protected def setUidToNodeIdIfNeed[T](
      nodeCtx: FlinkCustomNodeContext,
      stream: SingleOutputStreamOperator[T]
  ): SingleOutputStreamOperator[T] =
    ExplicitUidInOperatorsSupport.setUidIfNeedJava(explicitUidInStatefulOperators(nodeCtx), nodeCtx.nodeId)(stream)

  /**
   * Rewrite it if you wan to change globally configured behaviour of setting uid with local one
   */
  @Public
  protected def explicitUidInStatefulOperators(nodeCtx: FlinkCustomNodeContext): Boolean =
    ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators(nodeCtx)

}

object ExplicitUidInOperatorsSupport {

  def setUidIfNeed[T](explicitUidInStatefulOperators: Boolean, uidValue: String)(
      stream: DataStream[T]
  ): DataStream[T] = {
    if (explicitUidInStatefulOperators) {
      stream match {
        case operator: SingleOutputStreamOperator[T] => operator.uid(uidValue)
        case _ => throw new UnsupportedOperationException("Only supported for single output operator.")
      }
    } else
      stream
  }

  def setUidIfNeedSink[T](explicitUidInStatefulOperators: Boolean, uidValue: String)(
      stream: DataStreamSink[T]
  ): DataStreamSink[T] = {
    if (explicitUidInStatefulOperators)
      stream.uid(uidValue)
    else
      stream
  }

  def setUidIfNeedJava[T](explicitUidInStatefulOperators: Boolean, uidValue: String)(
      stream: SingleOutputStreamOperator[T]
  ): SingleOutputStreamOperator[T] = {
    if (explicitUidInStatefulOperators)
      stream.uid(uidValue)
    else
      stream
  }

  @PublicEvolving
  def defaultExplicitUidInStatefulOperators(nodeCtx: FlinkCustomNodeContext): Boolean =
    defaultExplicitUidInStatefulOperators(nodeCtx.globalParameters)

  @PublicEvolving
  def defaultExplicitUidInStatefulOperators(globalParameters: Option[NkGlobalParameters]): Boolean =
    globalParameters.flatMap(_.configParameters).flatMap(_.explicitUidInStatefulOperators).getOrElse(true)

}
