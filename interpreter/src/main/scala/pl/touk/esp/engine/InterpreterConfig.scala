package pl.touk.esp.engine

import pl.touk.esp.engine.api._
import pl.touk.esp.engine.util.LoggingListener

import scala.concurrent.ExecutionContext

class InterpreterConfig(val services: Map[String, Service],
                        val listeners: Seq[ProcessListener] = Seq(new LoggingListener)) {

  def open(implicit ec: ExecutionContext) = {
    services.foreach { case (_, s) => s.open }
  }

  def close() = {
    services.foreach { case (_, s) => s.close() }
  }

}