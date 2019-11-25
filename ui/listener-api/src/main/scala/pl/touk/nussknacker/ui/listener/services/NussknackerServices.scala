package pl.touk.nussknacker.ui.listener.services

import scala.concurrent.Future

case class NussknackerServices(pullProcessRepository: PullProcessRepository[Future])
