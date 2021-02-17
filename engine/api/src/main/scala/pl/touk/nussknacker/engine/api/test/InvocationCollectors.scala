package pl.touk.nussknacker.engine.api.test

import scala.concurrent.{ExecutionContext, Future}

object InvocationCollectors {

  type ToCollect = Any

  case class TransmissionNames(invocationName: String, resultName: String)

  object TransmissionNames {
    val default: TransmissionNames = TransmissionNames("invocation", "result")
  }

  case class CollectableAction[A](toCollect: () => ToCollect, result: A)

  trait ServiceInvocationCollector {
    def collect[A](request: => ToCollect, mockValue: Option[A])(action: => Future[A], names: TransmissionNames = TransmissionNames.default)
                  (implicit ec: ExecutionContext): Future[A]

    def collectWithResponse[A](request: => ToCollect, mockValue: Option[A])(action: => Future[CollectableAction[A]], names: TransmissionNames = TransmissionNames.default)
                              (implicit ec: ExecutionContext): Future[A]
  }

}