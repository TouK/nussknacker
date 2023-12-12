package pl.touk.nussknacker.engine.lite.api.utils.sources

import cats.Monad
import cats.data.{Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.component.{ComponentInfo, ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.{Lifecycle, ScenarioProcessingContext}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSource

import scala.language.higherKinds
import scala.util.Try

trait BaseLiteSource[T] extends LiteSource[T] with Lifecycle {

  protected var context: EngineRuntimeContext          = _
  protected var contextIdGenerator: ContextIdGenerator = _

  def nodeId: NodeId

  override def open(context: EngineRuntimeContext): Unit = {
    this.context = context
    this.contextIdGenerator = context.contextIdGenerator(nodeId.id)
  }

  override def createTransformation[F[_]: Monad](
      componentContext: customComponentTypes.CustomComponentContext[F]
  ): T => ValidatedNel[ErrorType, ScenarioProcessingContext] =
    record =>
      Validated
        .fromEither(Try(transform(record)).toEither)
        .leftMap(ex =>
          NuExceptionInfo(
            Some(NodeComponentInfo(componentContext.nodeId, ComponentType.Source, "unknown")),
            ex,
            ScenarioProcessingContext(contextIdGenerator.nextContextId())
          )
        )
        .toValidatedNel

  def transform(record: T): ScenarioProcessingContext

}
