package pl.touk.esp.engine

import pl.touk.esp.engine.api._
import pl.touk.esp.engine.util.LoggingListener

class InterpreterConfig(val services: Map[String, Service],
                        val listeners: Seq[ProcessListener] = Seq(new LoggingListener))