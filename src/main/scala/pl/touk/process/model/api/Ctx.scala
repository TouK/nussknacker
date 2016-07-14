package pl.touk.process.model.api

import org.slf4j.LoggerFactory

object Ctx {
  private val logger = LoggerFactory.getLogger("monitor")
}

case class Ctx(globals: Map[String, Any], data: Map[String, Any], services: Map[String, Service],
               listeners: Set[ProcessListener] = Set()) {

  def apply[T](name: String): T = vars.getOrElse(name,
    throw new RuntimeException(s"Unknown variable $name")).asInstanceOf[T]

  def vars: Map[String, Any] = globals ++ data

  def log(message: String, args: String*): Unit = Ctx.logger.info(message, args)

  def withData(name: String, value: Any): Ctx = copy(data = data + (name -> value))
}