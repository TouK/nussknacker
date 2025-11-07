package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.process.ProcessName

import java.util.UUID
import scala.jdk.CollectionConverters._

object Context {

  def apply(id: ContextId): Context = Context(id, Map.empty, None, None)

  def apply(id: ContextId, variables: Map[String, Any]): Context =
    Context(id, variables, None, None)

  // The dummy Context should only be used in test suites, perhaps also Nu scenario test mechanism or examples
  def dummy: Context = Context(ContextId.dummy)

}

object TraceId {

  def generate() =
    TraceId(UUID.randomUUID())

  def apply(uuid: UUID) =
    new TraceId(uuid.toString)

}

final case class TraceId(value: String) {
  override def toString: String = value
}

final case class ContextId private (
    scenarioName: ProcessName,
    originatingNodeId: NodeId,
    taskId: Long,
    index: Long,
    // We need to use java.util.List instead of the Scala List implementation, because of Flink typing issues:
    // - the Flink typing of Context and ContextId is handled in `object ContextTypeHelpers`
    // - we have our own TypeInformation implementations for Scala Map (TypedScalaMapTypeInformation) and case classes
    // - but we do not have such implementation for Scala List and creating it would require quite huge, error-prone custom implementation
    // - the Flink's ListTypeInfo does not support Scala List, because it cannot handle `Nil` - it has type List[Unknown], which causes problems with typing and casting
    // - as a result, we use java List internally in the ContextId, but we hide that fact in the interface
    private val contextIdPath: java.util.List[ContextIdPathPart],
) {

  def path: List[ContextIdPathPart] = contextIdPath.asScala.toList

  def withContextIdPathPart(nodeId: NodeId, value: String): ContextId = {
    val copied = copy(contextIdPath = new java.util.ArrayList(contextIdPath))
    copied.contextIdPath.add(ContextIdPathPart(nodeId, value))
    copied
  }

  lazy val legacyString: String = List(
    List(scenarioName.value),
    List(originatingNodeId.id),
    List(taskId.toString),
    List(index.toString),
    contextIdPath.asScala.map(_.value),
  ).flatten.mkString("-")

}

case class ContextIdPathPart(nodeId: NodeId, value: String)

object ContextId {

  // Override and hide the default apply, in order to hide java list
  private def apply(
      scenarioName: ProcessName,
      originatingNodeId: NodeId,
      taskId: Long,
      index: Long,
      contextIdPath: java.util.List[ContextIdPathPart]
  ): ContextId =
    new ContextId(scenarioName, originatingNodeId, taskId, index, contextIdPath)

  def apply(
      scenarioName: ProcessName,
      originatingNodeId: NodeId,
      taskId: Long,
      index: Long,
      path: List[ContextIdPathPart] = List.empty
  ): ContextId =
    new ContextId(scenarioName, originatingNodeId, taskId, index, path.asJava)

  // The dummy ContextId should only be used in test suites, perhaps also Nu scenario test mechanism or examples
  def dummy: ContextId = new ContextId(ProcessName("dummy"), NodeId("dummy"), 0, 0, List.empty.asJava)
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
    parentContext: Option[Context],
    /**
     * Optional internal tracking ID used for debugging, monitoring, or tracing the execution flow.
     * For developer use only – not exposed to process designers or end users.
     * Helps propagate correlation IDs across system components.
     */
    traceId: Option[TraceId]
) {

  def withContextIdPathPart(nodeId: NodeId, transformation: String): Context =
    copy(id = id.withContextIdPathPart(nodeId, transformation))

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

  def withTraceId(value: TraceId): Context =
    copy(traceId = Some(value))

  def pushNewContext(variables: Map[String, Any]): Context = {
    Context(id, variables, Some(this), traceId)
  }

  def unsafeTraceId: TraceId =
    traceId.getOrElse(throw new IllegalArgumentException(s"Context $id doesn't contain traceId field."))

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
