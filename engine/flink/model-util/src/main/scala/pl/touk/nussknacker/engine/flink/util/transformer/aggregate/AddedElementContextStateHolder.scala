package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.{Context => NkContext}
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap
import FlinkRangeMap._
import org.apache.flink.api.common.typeinfo.TypeInformation

import scala.language.higherKinds

trait AddedElementContextStateHolder[MapT[K,V]] {

  // We keep tracking context of added elements, because during handling timer we need to emit some context with e.g. key prepared before aggregation.
  // Context of element that left the slide can be confusing but we haven't got anything better
  @transient
  protected var addedElementContext: ValueState[MapT[Long, NkContext]] = _

  protected implicit def rangeMap: FlinkRangeMap[MapT]

  protected def invalidateAddedElementContextState(stateValue: MapT[Long, AnyRef]): Unit = {
    addedElementContext.update(readAddedElementContextOrInitial().filterKeys(stateValue.toScalaMapRO.contains))
  }

  protected def readAddedElementContextOrInitial(): MapT[Long, NkContext] =
    Option(addedElementContext.value()).getOrElse(rangeMap.empty[Long, NkContext])

  protected def addedElementContextDescriptor: ValueStateDescriptor[MapT[Long, NkContext]] =
    new ValueStateDescriptor[MapT[Long, NkContext]]("addedElementContext",
      rangeMap.typeInformation[Long, NkContext](implicitly[TypeInformation[Long]], nkContextTypeInformation))

  // TODO pass nk context type information for fast (de)serialization
  protected def nkContextTypeInformation: TypeInformation[NkContext] = {
    implicitly[TypeInformation[NkContext]]
  }

}
