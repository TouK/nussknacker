package pl.touk.nussknacker.genericmodel

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.flink.util.transformer.{AggregateTransformer, PreviousValueTransformer}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator
import pl.touk.nussknacker.genericmodel.sinks.GenericKafkaJsonSink
import pl.touk.nussknacker.genericmodel.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}

class GenericConfigCreator extends EmptyProcessConfigCreator {

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "previousValue" -> WithCategories(PreviousValueTransformer, "Default"),
    "aggregate" -> WithCategories(AggregateTransformer, "Default")
  )

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    Map("kafka-json" -> WithCategories(new GenericJsonSourceFactory(kafkaConfig), "Default"),
        "kafka-typed-json" -> WithCategories(new GenericTypedJsonSourceFactory(kafkaConfig), "Default")
    )
  }

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    Map(
      "kafka-sink" -> WithCategories(new GenericKafkaJsonSink(kafkaConfig), "Default")
    )
  }

  override def expressionConfig(config: Config): ExpressionConfig = ExpressionConfig(
    Map(

    ),
    List()
  )
}
