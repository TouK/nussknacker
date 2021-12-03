package pl.touk.nussknacker.engine.lite.kafka.api

import cats.Monad
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{Context, Lifecycle}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSource

import scala.language.higherKinds

trait LiteKafkaSource extends LiteSource[ConsumerRecord[Array[Byte], Array[Byte]]] with Lifecycle {

  def topics: List[String]

  private var context: EngineRuntimeContext = _

  override def open(context: EngineRuntimeContext): Unit = {
    this.context = context
  }

  override def createTransformation[F[_] : Monad](evaluateLazyParameter: customComponentTypes.CustomComponentContext[F]): ConsumerRecord[Array[Byte], Array[Byte]] => ValidatedNel[ErrorType, Context] = record =>
    Valid(deserialize(context, record))

  // TODO: error handling
  def deserialize(context: EngineRuntimeContext, record: ConsumerRecord[Array[Byte], Array[Byte]]): Context

}
