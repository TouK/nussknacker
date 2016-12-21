package pl.touk.esp.engine.api

import pl.touk.esp.engine.api.lazyy.LazyContext

case class Context(id: String, variables: Map[String, Any] = Map.empty,
                   lazyContext: LazyContext = LazyContext()) {

  def apply[T](name: String): T =
    getOrElse(name, throw new RuntimeException(s"Unknown variable: $name"))

  def getOrElse[T](name: String, default: => T) =
    get(name).getOrElse(default)

  def get[T](name: String): Option[T] =
    variables.get(name).map(_.asInstanceOf[T])

  def modifyVariable[T](name: String, f: T => T): Context =
    withVariable(name, f(apply(name)))

  def modifyOptionalVariable[T](name: String, f: Option[T] => T): Context =
    withVariable(name, f(get[T](name)))

  def withVariable(name: String, value: Any): Context =
    withVariables(Map(name -> value))

  def withVariables(otherVariables: Map[String, Any]): Context =
    copy(variables = variables ++ otherVariables)

  def withLazyContext(lazyContext: LazyContext) =
    copy(lazyContext = lazyContext)

}
