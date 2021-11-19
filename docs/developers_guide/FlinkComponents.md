# Flink components
 
Sources, sinks and custom transformations are based on 
Flink API. Access to various helpers is provided by 
`FlinkCustomNodeContext`. Special care should be taken to handle
- lifecycle
- exception handling properly

:warning: **Flink components should not extend Lifecycle** - it won't be handled properly

## Sources

Source are defined with [FlinkSource](https://github.com/TouK/nussknacker/blob/staging/engine/flink/api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkSource.scala).
In most cases (when you only pass one variable to initial `Context`) it's easier to use `BaseFlinkSource`.

## Sinks

Sinks are defined using [FlinkSink](https://github.com/TouK/nussknacker/blob/staging/engine/flink/api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkSink.scala). Again, `BasicFlinkSink` is provided for simple cases. 

## Custom transformers

In Flink, custom transformation can arbitrarily change `DataStream[Context]`, it's implemented with [FlinkCustomStreamTransformation](https://github.com/TouK/nussknacker/blob/staging/engine/flink/api/src/main/scala/pl/touk/nussknacker/engine/flink/api/process/FlinkCustomStreamTransformation.scala)
