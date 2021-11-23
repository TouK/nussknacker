package pl.touk.nussknacker.engine.requestresponse.http.logging

import akka.event.Logging
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.ActorMaterializer
import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}

trait RequestResponseLogger {
  def loggingDirective(processName: String)(implicit mat: ActorMaterializer): Directive0
}

object RequestResponseLogger {
  def get(classLoader: ClassLoader): RequestResponseLogger = {
    compound {
      Multiplicity(ScalaServiceLoader.load[RequestResponseLogger](classLoader)) match {
        case One(logger) => NonEmptyList.of(logger)
        case Many(loggers) => NonEmptyList.fromListUnsafe(loggers)
        case Empty() => NonEmptyList.of(RequestResponseLogger.default)
      }
    }
  }

  private def compound(loggers: NonEmptyList[RequestResponseLogger]): RequestResponseLogger = {
    new RequestResponseLogger {
      override def loggingDirective(processName: String)(implicit mat: ActorMaterializer): Directive0 = {
        loggers.map(_.loggingDirective(processName)).reduceLeft(_ & _)
      }
    }
  }

  def default: RequestResponseLogger = new RequestResponseLogger {
    override def loggingDirective(processName: String)(implicit mat: ActorMaterializer): Directive0 = {
      DebuggingDirectives.logRequestResult((s"request-response-$processName", Logging.DebugLevel))
    }
  }
}
