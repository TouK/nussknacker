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
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource

import scala.language.higherKinds
import scala.util.Try

trait LiteKafkaSource extends BaseLiteSource[ConsumerRecord[Array[Byte], Array[Byte]]] {

  def topics: List[String]

}
