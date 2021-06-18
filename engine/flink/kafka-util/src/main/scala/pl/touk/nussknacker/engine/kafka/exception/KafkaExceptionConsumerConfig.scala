package pl.touk.nussknacker.engine.kafka.exception

case class KafkaExceptionConsumerConfig(topic: String,
                                        //quite large to be able to show nested exception
                                        stackTraceLengthLimit: Int = 50,
                                        includeHost: Boolean = true,
                                        includeInputEvent: Boolean = false,
                                        //by default we use temp producer, as it's more robust
                                        useSharedProducer: Boolean = false,
                                        additionalParams: Map[String, String] = Map.empty)