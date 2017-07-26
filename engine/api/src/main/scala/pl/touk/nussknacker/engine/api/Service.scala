package pl.touk.nussknacker.engine.api

/** Interface for Enricher/Processor.
  * It has to have method annotated with [[pl.touk.nussknacker.engine.api.MethodToInvoke]] and this method will be invoked for every service invocation.
* */
trait Service extends Lifecycle