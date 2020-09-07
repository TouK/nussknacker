package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.lazyy.LazyContext

object Context {

  def apply(id: String) : Context = Context(id, Map.empty, LazyContext(id), None)

}

class ContextId(val id: String) extends AnyRef

case class Context(id: String, variables: Map[String, Any],
                   lazyContext: LazyContext, parentContext: Option[Context]) {

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

  def withLazyContext(lazyContext: LazyContext) : Context =
    copy(lazyContext = lazyContext)

  def pushNewContext(variables: Map[String, Any]) : Context = {
    Context(id, variables, lazyContext, Some(this))
  }

  def popContext : Context =
    parentContext
      .map(parent => parent.copy(lazyContext = parent.lazyContext.withEvaluatedValues(this.lazyContext.evaluatedValues)))
      .getOrElse(throw new RuntimeException("No parent context available"))

}
