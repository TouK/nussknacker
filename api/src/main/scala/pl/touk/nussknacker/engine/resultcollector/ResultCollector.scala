package pl.touk.nussknacker.engine.resultcollector

import cats.Monad
import cats.implicits._
import pl.touk.nussknacker.engine.api.ContextId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ToCollect

import scala.language.higherKinds
/*
  This is base API for collecting results of invocations of services (in the future - also sinks, custom nodes etc.),
  used by tests from UI, service queries from UI etc.
 */
trait ResultCollector extends Serializable {

  /*
    @request - object that should be recorded (e.g. REST request)
    @mockValue - value to return when invocation is stubbed, None means normal invocation should proceed
    @action - e.g. REST service invocation, stubbed e.g. during tests. CollectableAction contains not only result, but also can contain
      "raw" format, e.g. REST service response
    @names - in more complex scenarios we may want to distinguish different invocations
   */
  def collectWithResponse[A, F[_] : Monad](contextId: ContextId,
                                           nodeId: NodeId,
                                           request: => ToCollect,
                                           mockValue: Option[A],
                                           action: => F[CollectableAction[A]],
                                           names: TransmissionNames): F[A]

}

case class CollectableAction[A](toCollect: () => ToCollect, result: A)

case class TransmissionNames(invocationName: String, resultName: String)

object TransmissionNames {
  val default: TransmissionNames = TransmissionNames("invocation", "result")
}


//just invoke the action and ignore raw output from CollectableAction
object ProductionServiceInvocationCollector extends ResultCollector {
  override def collectWithResponse[A, F[_]:Monad](contextId: ContextId, nodeId: NodeId, request: => ToCollect, mockValue: Option[A], action: => F[CollectableAction[A]], names: TransmissionNames): F[A] = {
    action.map(_.result)
  }
}

//Sanity check, when we compile objects just for validation, we don't really want to invoke e.g. REST services etc.
object PreventInvocationCollector extends ResultCollector {
  override def collectWithResponse[A, F[_] : Monad](contextId: ContextId, nodeId: NodeId, request: => ToCollect, mockValue: Option[A], action: => F[CollectableAction[A]], names: TransmissionNames): F[A] = {
    throw new IllegalArgumentException("Service invocations should not be used in this context")
  }
}

