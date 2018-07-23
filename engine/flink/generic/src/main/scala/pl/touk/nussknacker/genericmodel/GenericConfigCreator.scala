package pl.touk.nussknacker.genericmodel

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.transformer.{AggregateTransformer, PreviousValueTransformer}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSink
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}

class GenericConfigCreator extends EmptyProcessConfigCreator {

  protected def defaultCategory[T](obj: T) = WithCategories(obj, "Default")

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "previousValue" -> defaultCategory(PreviousValueTransformer),
    "aggregate" -> defaultCategory(AggregateTransformer)
  )

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    Map("kafka-json" -> defaultCategory(new GenericJsonSourceFactory(kafkaConfig)),
        "kafka-typed-json" -> defaultCategory(new GenericTypedJsonSourceFactory(kafkaConfig))
    )
  }

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    Map(
      "kafka-sink" -> defaultCategory(new GenericKafkaJsonSink(kafkaConfig))
    )
  }

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory
    = ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler(_))

  import pl.touk.nussknacker.engine.util.functions._
  override def expressionConfig(config: Config): ExpressionConfig = ExpressionConfig(
    Map(
      "GEO" -> defaultCategory(geo),
      "NUMERIC" -> defaultCategory(numeric),
      "DATE" -> defaultCategory(date)
    ),
    List()
  )
}
