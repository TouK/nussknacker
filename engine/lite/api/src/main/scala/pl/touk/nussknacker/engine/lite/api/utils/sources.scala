package pl.touk.nussknacker.engine.lite.api.utils

import cats.Monad
import cats.data.{Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.{Context, Lifecycle}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSource

import scala.language.higherKinds
import scala.util.Try

object sources {

  trait BaseLiteSource[T] extends LiteSource[T] with Lifecycle {

    def nodeId: NodeId

    protected var context: EngineRuntimeContext = _

    protected var contextIdGenerator: ContextIdGenerator = _

    override def open(context: EngineRuntimeContext): Unit = {
      this.context = context
      this.contextIdGenerator = context.contextIdGenerator(nodeId.id)
    }

    override def createTransformation[F[_] : Monad](componentContext: customComponentTypes.CustomComponentContext[F]): T => ValidatedNel[ErrorType, Context] =
      record => Validated.fromEither(Try(transform(record)).toEither)
          .leftMap(ex => NuExceptionInfo(Some(componentContext.nodeId), ex, Context(contextIdGenerator.nextContextId()))).toValidatedNel

    def transform(record: T): Context

  }

}
