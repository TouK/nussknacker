package pl.touk.nussknacker.engine.api.component

/**
  * Marks a custom transformation type as able to route a node's additional outputs. Only Flink's multi-output
  * transformation carries it today, so connecting a declared additional output fails validation everywhere else -
  * including on Flink, when the component returns the single-output transformation. An unconnected declaration costs
  * nothing and passes. It lives here, not in a Flink module, because that check runs in the engine-agnostic compiler.
  */
trait SupportsMultipleOutputs
