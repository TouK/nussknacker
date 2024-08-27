package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.component.{AllProcessingModesComponent, Component}

/**
  * Hook for using Apache Flink API directly.
  * See examples in pl.touk.nussknacker.engine.example.custom
  *
  * IMPORTANT lifecycle notice:
  * Implementations of this class *must not* allocate resources (connections, file handles etc.)
  *
  * To make implementation easier, by default, stream transformers handle all processing modes. If you have some
  * processing mode specific component, you should override allowedProcessingModes method
**/
//TODO this could be scala-trait, but we leave it as abstract class for now for java compatibility
//We should consider separate interfaces for java implementation, but right now we convert ProcessConfigCreator
//from java to scala one and is seems difficult to convert java CustomStreamTransformer, Service etc. into scala ones
abstract class CustomStreamTransformer extends Component with AllProcessingModesComponent {

  // For now it is only supported by Flink streaming runtime
  def canBeEnding: Boolean = false

}
