package pl.touk.nussknacker.engine.api

object Context {

  def apply(id: String) : Context = Context(id, Map.empty, None)

}

case class ContextId(value: String)

case class Context(id: String, variables: Map[String, Any], parentContext: Option[Context]) {

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

  def pushNewContext(variables: Map[String, Any]) : Context = {
    Context(id, variables, Some(this))
  }

  def popContext : Context =
    parentContext.getOrElse(throw new RuntimeException("No parent context available"))

}
