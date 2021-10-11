package pl.touk.nussknacker.engine.api.test

import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{ServiceInvocationCollector, ToCollect}
import pl.touk.nussknacker.engine.resultcollector.{CollectableAction, TransmissionNames}

import scala.concurrent.{ExecutionContext, Future}

//This implementation should be used only for unit tests of services
object EmptyInvocationCollector {
  implicit val Instance: ServiceInvocationCollector = new ServiceInvocationCollector {
    override def collectWithResponse[A](request: => ToCollect, mockValue: Option[A])
                                       (action: => Future[CollectableAction[A]], names: TransmissionNames)
                                       (implicit ec: ExecutionContext): Future[A] = action.map(_.result)
  }
}
