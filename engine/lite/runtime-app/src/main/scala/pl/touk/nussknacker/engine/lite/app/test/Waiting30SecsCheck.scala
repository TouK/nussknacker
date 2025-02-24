package pl.touk.nussknacker.engine.lite.app.test

import org.apache.pekko.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging

import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/*
This class is for purpose of testing k8s deployment status. If you wan to turn it on, you must add:
```
pekko.management.health-checks.readiness-checks {
  wait-30secs = "pl.touk.nussknacker.engine.lite.app.test.Waiting30SecsCheck"
}
```
to application.conf
 */

class Waiting30SecsCheck(system: ActorSystem) extends (() => Future[Boolean]) with LazyLogging {

  private var firstAttemptTimeOpt = Option.empty[Instant]

  override def apply(): Future[Boolean] = {
    Future {
      synchronized {
        firstAttemptTimeOpt match {
          case None =>
            firstAttemptTimeOpt = Some(Instant.now)
            false
          case Some(firstAttemptTime) =>
            val duration = Duration.between(firstAttemptTime, Instant.now)
            logger.info(s"Duration from first attempt: $duration")
            duration.compareTo(Duration.ofSeconds(30)) >= 0
        }
      }
    }
  }

}
