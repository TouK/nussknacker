# Lite components

:::caution

Streaming-Lite API should not be considered stable at the moment. 

:::
         
## Common features of Lite components

Base API of Lite components is [here](https://github.com/TouK/nussknacker/tree/staging/engine/lite/components-api/src/main/scala/pl/touk/nussknacker/engine/lite/api)
This is a generic API that is used by Streaming-Lite engine, but can be used to implement own engines, e.g. running in Request-Response way, or embedded in your application.
For that reason, this API has a few parameters, that are fixed in StreamingLite:
- `F` - the effect monad, in Streaming-Lite it is Scala `Future`
- `Input` - the type of data (e.g. events processed by the given engine). In Streaming-Lite it's `ConsumerRecord[Array[Byte], Array[Byte]]` 
- `Result` - the type of data produced by sinks of a given engine. In Streaming-Lite it's `ProducerRecord[Array[Byte], Array[Byte]]`
You can see [sample](https://github.com/TouK/nussknacker/blob/staging/engine/lite/runtime/src/test/scala/pl/touk/nussknacker/engine/lite/sample.scala) implementation of different Lite engine.

The data can be processed in [microbatches](https://github.com/TouK/nussknacker/blob/staging/engine/lite/components-api/src/main/scala/pl/touk/nussknacker/engine/lite/api/commonTypes.scala#L18), 
in Streaming-Lite the batch contains all the records that are read with single a `consumer.poll`.
                
Processing of a single batch ends with a list of results, each of the results may by either `Result` in case of success, 
or `NuExceptionInfo` in case of an error. 

## Sources and sinks

### Streaming

In Streaming-Lite sources and sinks are relatively simple, as all sources and sinks are based on Kafka:
- A [source](https://github.com/TouK/nussknacker/blob/staging/engine/lite/kafka/components-api/src/main/scala/pl/touk/nussknacker/engine/lite/kafka/api/LiteKafkaSource.scala) 
transforms `ConsumerRecord` to Nussknacker [Context](https://github.com/TouK/nussknacker/blob/staging/components-api/src/main/scala/pl/touk/nussknacker/engine/api/Context.scala). 
- A [sink](https://github.com/TouK/nussknacker/blob/staging/engine/lite/components-api/src/main/scala/pl/touk/nussknacker/engine/lite/api/customComponentTypes.scala#L51) creates `ProducerRecord` from its parameters.
- Both may return error.

### Request-Response

In Request-Response / Lite sources and sinks act slightly different. Both create a combination of a HTTP request (source) and a response(sink)
- A [source](https://github.com/TouK/nussknacker/blob/staging/engine/lite/request-response/components-api/src/main/scala/pl/touk/nussknacker/engine/requestresponse/api/RequestResponseSourceFactory.scala) can handle GET and POST http requests
- A [sink](https://github.com/TouK/nussknacker/blob/staging/engine/lite/request-response/components-api/src/main/scala/pl/touk/nussknacker/engine/requestresponse/api/RequestResponseSourceFactory.scala) is responsible for providing response payload

## Custom transformers

Generic [Lite custom component](https://github.com/TouK/nussknacker/blob/staging/engine/lite/components-api/src/main/scala/pl/touk/nussknacker/engine/lite/api/customComponentTypes.scala#L31) 
is designed in continuation-passing style. 
Some helper [traits](https://github.com/TouK/nussknacker/blob/staging/engine/lite/components-api/src/main/scala/pl/touk/nussknacker/engine/lite/api/utils/transformers.scala) are provided, 
you can also look at [base components](https://github.com/TouK/nussknacker/tree/staging/engine/lite/components/base/src/main/scala/pl/touk/nussknacker/engine/lite/components).

### Capabilities transformer

Some transformers can work with arbitrary effect `F`, the examples are basic components
like union or for-each. If custom component depends on the specific effect 
(e.g. in Streaming-Lite you want to invoke a custom service which returns `Future[_]`),
you can use `CapabilityTransformer` provided by `CustomComponentContext` to return desired effect. If this component will be used 
with a different effect, error will be returned by scenario's compiler. 
See [example](https://github.com/TouK/nussknacker/blob/staging/engine/lite/runtime/src/test/scala/pl/touk/nussknacker/engine/lite/sample.scala#L66) for the details.
