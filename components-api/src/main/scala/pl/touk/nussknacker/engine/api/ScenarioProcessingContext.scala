package pl.touk.nussknacker.engine.api

import java.util.UUID
import scala.util.Random

object ScenarioProcessingContext {

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
  def withInitialId: ScenarioProcessingContext = {
    ScenarioProcessingContext(initialContextIdPrefix + new UUID(random.nextLong(), random.nextLong()).toString)
  }

  def apply(id: String): ScenarioProcessingContext = ScenarioProcessingContext(id, Map.empty, None)

}

case class ScenarioProcessingContextId(value: String)

/**
 * Context is container for variables used in expression evaluation
 *
 * @param id            correlation id/trace id used for tracing (logs, error presentation) and for tests mechanism, it should be always defined
 * @param variables     variables available in evaluation
 * @param parentContext context used for scopes handling, mainly for fragment invocation purpose
 */
case class ScenarioProcessingContext(
    id: String,
    variables: Map[String, Any],
    parentContext: Option[ScenarioProcessingContext]
) {

  def appendIdSuffix(suffix: String): ScenarioProcessingContext =
    copy(id = s"$id-$suffix")

  def apply[T](name: String): T =
    getOrElse(name, throw new RuntimeException(s"Unknown variable: $name"))

  def getOrElse[T](name: String, default: => T): T =
    get(name).getOrElse(default)

  def get[T](name: String): Option[T] =
    variables.get(name).map(_.asInstanceOf[T])

  def modifyVariable[T](name: String, f: T => T): ScenarioProcessingContext =
    withVariable(name, f(apply(name)))

  def modifyOptionalVariable[T](name: String, f: Option[T] => T): ScenarioProcessingContext =
    withVariable(name, f(get[T](name)))

  def withVariable(name: String, value: Any): ScenarioProcessingContext =
    withVariables(Map(name -> value))

  def withVariables(otherVariables: Map[String, Any]): ScenarioProcessingContext =
    copy(variables = variables ++ otherVariables)

  def pushNewContext(variables: Map[String, Any]): ScenarioProcessingContext = {
    ScenarioProcessingContext(id, variables, Some(this))
  }

  // it returns all variables from context including parent tree
  def allVariables: Map[String, Any] = {
    def extractContextVariables(context: ScenarioProcessingContext): Map[String, Any] =
      context.parentContext.map(extractContextVariables).getOrElse(Map.empty) ++ context.variables

    extractContextVariables(this)
  }

  def clearUserVariables: ScenarioProcessingContext = {
    // clears variables from context but leaves technical variables, hidden from user
    val variablesToLeave = Set(VariableConstants.EventTimestampVariableName)
    copy(variables = variables.filter { case (k, _) => variablesToLeave(k) })
  }

}
