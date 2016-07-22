package pl.touk.esp.engine.api

import java.lang.reflect.Method

import pl.touk.esp.engine.InterpreterConfig

case class Context(config: InterpreterConfig,
                   variables: Map[String, Any] = Map.empty) {

  def listeners: Seq[ProcessListener] =
    config.listeners

  def expressionFunctions: Map[String, Method] =
    config.expressionFunctions

  def service(id: String) = config.services.getOrElse(
    id,
    throw new RuntimeException(s"Missing service: $id")
  )

  def modifyVariable[T](name: String, f: T => T): Context =
    withVariable(name, f(apply(name)))

  def modifyOptionalVariable[T](name: String, f: Option[T] => T): Context =
    withVariable(name, f(get[T](name)))

  def withVariable(name: String, value: Any): Context =
    copy(variables = variables + (name -> value))

  def apply[T](name: String): T =
    getOrElse(name, throw new RuntimeException(s"Unknown variable: $name"))

  def getOrElse[T](name: String, default: => T) =
    get(name).getOrElse(default)

  def get[T](name: String): Option[T] =
    variables.get(name).map(_.asInstanceOf[T])

}
