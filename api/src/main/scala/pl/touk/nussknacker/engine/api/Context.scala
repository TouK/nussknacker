package pl.touk.nussknacker.engine.api

import java.util.UUID
import scala.util.Random

object Context {

  // prefix is to distinguish between externally provided and internal (initially created) id
  private val initialContextIdPrefix = "initial-"

  /**
    * For performance reasons, is used unsecure random - see UUIDBenchmark for details. In this case random correlation id
    * is used only for internal purpose so is not important in security context.
    */
  private val random = new Random()

  /**
    * Deprecated: should be used ContextIdGenerator e.g. via EngineRuntimeContext.contextIdGenerator
    * Should be used for newly created context - when there is no suitable external correlation / tracing id
    */
  def withInitialId: Context = {
    Context(initialContextIdPrefix + new UUID(random.nextLong(), random.nextLong()).toString)
  }

  def apply(id: String) : Context = Context(id, Map.empty, None)

}

case class ContextId(value: String)

/**
  * Context is container for variables used in expression evaluation
  * @param id correlation id/trace id used for tracing (logs, error presentation) and for tests mechanism, it should be always defined
  * @param variables variables available in evaluation
  * @param parentContext context used for scopes handling, mainly for subprocess invocation purpose
  */
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

  def clearVariablesExcept(variablesToLeave: Set[String]) : Context =
    copy(variables = variables.filterKeys(variablesToLeave))

}
