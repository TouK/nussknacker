package pl.touk.nussknacker.engine.lite.api.utils.sources

import cats.Monad
import cats.data.{Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.{Context, Lifecycle, NodeId, TraceId}
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ContextVariables
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSource

import scala.language.higherKinds
import scala.util.Try

object BaseLiteSource {
  val DefaultTraceIdHeader: String = "trace-id"
}

trait BaseLiteSource[T] extends LiteSource[T] with Lifecycle {

  protected var context: EngineRuntimeContext        = _
  private var contextIdGenerator: ContextIdGenerator = _

  def nodeId: NodeId

  override def open(context: EngineRuntimeContext): Unit = {
    this.context = context
    this.contextIdGenerator = context.contextIdGenerator(nodeId)
  }

  override def createTransformation[F[_]: Monad](
      componentContext: customComponentTypes.CustomComponentContext[F]
  ): T => ValidatedNel[ErrorType, Context] =
    record => {
      val context = Context(contextIdGenerator.nextContextId())

      Validated
        .fromEither(Try((transform(record), extractTraceId(record))).toEither)
        .map { case (contextVariables, traceId) =>
          val contextWithVariables = context.withVariables(contextVariables.variables)

          traceId
            .map(contextWithVariables.withTraceId)
            .getOrElse(contextWithVariables)
        }
        .leftMap(ex =>
          NuExceptionInfo(
            Some(
              NodeComponentInfo(componentContext.nodeId, componentContext.nodeName, ComponentType.Source, "unknown")
            ),
            ex,
            context
          )
        )
        .toValidatedNel
    }

  // for overriding purposes
  protected def extractTraceId(record: T): Option[TraceId] = None

  def transform(record: T): ContextVariables

}
