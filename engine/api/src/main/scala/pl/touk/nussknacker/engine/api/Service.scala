package pl.touk.nussknacker.engine.api

/** Interface for Enricher/Processor.
  * It has to have method annotated with [[pl.touk.nussknacker.engine.api.MethodToInvoke]] and this method will be invoked for every service invocation.
* */
//TODO this could be scala-trait, but we leave it as abstract class for now for java compatibility
//We should consider separate interfaces for java implementation, but right now we convert ProcessConfigCreator
//from java to scala one and is seems difficult to convert java CustomStreamTransformer, Service etc. into scala ones
abstract class Service extends Lifecycle