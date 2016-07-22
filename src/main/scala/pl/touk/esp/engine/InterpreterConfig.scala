package pl.touk.esp.engine

import java.lang.reflect.Method

import pl.touk.esp.engine.api.{ProcessListener, Service}

case class InterpreterConfig(services: Map[String, Service],
                             listeners: Seq[ProcessListener],
                             expressionFunctions: Map[String, Method])