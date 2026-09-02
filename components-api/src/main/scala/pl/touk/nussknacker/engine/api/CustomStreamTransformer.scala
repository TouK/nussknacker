package pl.touk.nussknacker.engine.api

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.component.{AllProcessingModesComponent, Component, ComponentOutput}

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

  /**
    * The node's outputs, in order: the head is the main output and the tail are the additional ones - every output
    * is named, the main one included. For now the tail is only supported by the Flink streaming runtime. Once any
    * additional output is connected, an unwired main output is a dead end rather than a scenario end, so
    * `canBeEnding` no longer applies to that node.
    */
  def outputs: NonEmptyList[ComponentOutput] = NonEmptyList.of(ComponentOutput.MainOutput)

}
