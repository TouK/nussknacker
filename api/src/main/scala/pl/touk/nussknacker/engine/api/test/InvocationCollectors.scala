package pl.touk.nussknacker.engine.api.test

import pl.touk.nussknacker.engine.resultcollector.{CollectableAction, TransmissionNames}

import scala.concurrent.{ExecutionContext, Future}

object InvocationCollectors {

  type ToCollect = Any

  /*
    This is base API for collecting results of invocations of services, used by tests from UI, service queries from UI etc.
   */
  trait ServiceInvocationCollector {

    /*
      @request - object that should be recorded (e.g. REST request)
      @mockValue - value to return when invocation is stubbed, None means normal invocation should proceed
      @action - e.g. REST service invocation, stubbed e.g. during tests
      @names - in more complex scenarios we may want to distinguish different invocations
     */
    def collect[A](request: => ToCollect, mockValue: Option[A])(action: => Future[A], names: TransmissionNames = TransmissionNames.default)
                  (implicit ec: ExecutionContext): Future[A] = {
      collectWithResponse[A](request, mockValue)(action.map(a => CollectableAction(() => "", a)), names)
    }

    /*
      @request - object that should be recorded (e.g. REST request)
      @mockValue - value to return when invocation is stubbed, None means normal invocation should proceed
      @action - e.g. REST service invocation, stubbed e.g. during tests. CollectableAction contains not only result, but also can contain
        "raw" format, e.g. REST service response
      @names - in more complex scenarios we may want to distinguish different invocations
     */
    def collectWithResponse[A](request: => ToCollect, mockValue: Option[A])(action: => Future[CollectableAction[A]], names: TransmissionNames = TransmissionNames.default)
                              (implicit ec: ExecutionContext): Future[A]
  }



}