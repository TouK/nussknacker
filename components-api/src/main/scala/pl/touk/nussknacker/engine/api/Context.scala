package pl.touk.nussknacker.engine.api

import scala.jdk.CollectionConverters._

object Context {

  def apply(id: ContextId): Context = Context(id, Map.empty, None)

  def apply(id: ContextId, variables: Map[String, Any]): Context =
    Context(id, variables, None)

  def dummy: Context = Context(ContextId.dummy)

}

case class ContextId(
    scenarioId: String,
    originatingNodeId: String,
    taskId: Long,
    index: Long,
    transformations: java.util.List[ContextIdTransformation] = java.util.List.of(),
) {

  def serialize: String = List(
    List(scenarioId),
    List(originatingNodeId),
    List(taskId.toString),
    List(index.toString),
    transformations.asScala.map(_.transformation),
  ).flatten.mkString("-")

}

case class ContextIdTransformation(nodeId: String, transformation: String)

object ContextId {
  object DummyContextId extends ContextId("dummy", "dummy", 0, 0, List.empty.asJava)

  def dummy: ContextId = DummyContextId
}

/**
 * Context is container for variables used in expression evaluation
 *
 * @param id            correlation id/trace id used for tracing (logs, error presentation) and for tests mechanism, it should be always defined
 * @param variables     variables available in evaluation
 * @param parentContext context used for scopes handling, mainly for fragment invocation purpose
 */
case class Context(
    id: ContextId,
    variables: Map[String, Any],
    parentContext: Option[Context]
) {

  def withContextIdTransformation(nodeId: String, transformation: String): Context = {
    id.transformations.add(ContextIdTransformation(nodeId, transformation))
    this
  }

  // TODO: all methods should has NotNothing type check to avoid situation when scala's compiler implicitly put Nothing
  //       into parameter
  def apply[T](name: String): T =
    getOrElse(name, throw new RuntimeException(s"Unknown variable: $name"))

  def getOrElse[T](name: String, default: => T): T =
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

  def pushNewContext(variables: Map[String, Any]): Context = {
    Context(id, variables, Some(this))
  }

  // it returns all variables from context including parent tree
  def allVariables: Map[String, Any] = {
    def extractContextVariables(context: Context): Map[String, Any] =
      context.parentContext.map(extractContextVariables).getOrElse(Map.empty) ++ context.variables

    extractContextVariables(this)
  }

  def clearUserVariables: Context = {
    // clears variables from context but leaves technical variables, hidden from user
    val variablesToLeave = Set(VariableConstants.EventTimestampVariableName)
    copy(variables = variables.filter { case (k, _) => variablesToLeave(k) })
  }

}
