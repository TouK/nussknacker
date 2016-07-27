package pl.touk.esp.engine

import java.lang.reflect.Method
import java.time.{LocalDate, LocalDateTime}

import pl.touk.esp.engine.api._
import pl.touk.esp.engine.util.LoggingListener

case class InterpreterConfig(services: Map[String, Service],
                             listeners: Seq[ProcessListener] = Seq(new LoggingListener),
                             expressionFunctions: Map[String, Method] = InterpreterConfig.DefaultExpressionFunctions)

object InterpreterConfig {

  final val DefaultExpressionFunctions: Map[String, Method] = {
    Map(
      "today" -> classOf[LocalDate].getDeclaredMethod("now"),
      "now" -> classOf[LocalDateTime].getDeclaredMethod("now")
    )
  }

}