package pl.touk.nussknacker.engine.lite.kafka.api

import cats.Monad
import cats.data.{Validated, ValidatedNel}
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{Context, ContextId, Lifecycle}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSource

import scala.language.higherKinds
import scala.util.Try

trait LiteKafkaSource extends LiteSource[ConsumerRecord[Array[Byte], Array[Byte]]] with Lifecycle {

  def topics: List[String]

  protected var context: EngineRuntimeContext = _

  override def open(context: EngineRuntimeContext): Unit = {
    this.context = context
  }

  protected def nextContextId: ContextId

  override def createTransformation[F[_] : Monad](componentContext: customComponentTypes.CustomComponentContext[F]): ConsumerRecord[Array[Byte], Array[Byte]] => ValidatedNel[ErrorType, Context] =
    record => {
      Validated.fromEither(Try(transform(record)).toEither)
        .leftMap(ex => NuExceptionInfo(Some(componentContext.nodeId), ex, Context(nextContextId.value))).toValidatedNel
    }

  def transform(record: ConsumerRecord[Array[Byte], Array[Byte]]): Context

}
