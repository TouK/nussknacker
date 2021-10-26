package pl.touk.nussknacker.ui.process.processingtypedata

import pl.touk.nussknacker.restmodel.process.ProcessingType

/**
 *  NOTICE: This is probably *temporary* solution. We want to be able to:
 *  - reload elements of ProcessingTypeData
 *  - have option to add new ProcessingTypeData dynamically (though currently it's not supported)
 *  - use only those elements which are needed in given place
 *
 *  In the future we'll probably have better way of controlling how ProcessingTypeData is used, e.g. by
 *    - custom Directives or custom routes.
 *    - appropriate variant of Reader monad :)
 *
 *  Now we just want to be able to easily check usages of ProcessingTypeData elements
 *
 *  Currently, the only implementation is map-based, but in the future it will allow to reload ProcessingTypeData related stuff
 *  without restarting the app
 */
trait ProcessingTypeDataProvider[+T] {

  def forType(typ: ProcessingType): Option[T]

  //TODO: replace with proper forType handling
  def forTypeUnsafe(typ: ProcessingType): T = forType(typ)
    .getOrElse(throw new IllegalArgumentException(s"Unknown typ: $typ, known types are: ${all.keys.mkString(", ")}"))

  def all: Map[ProcessingType, T]

  def mapValues[Y](fun: T => Y): ProcessingTypeDataProvider[Y] = {

    new ProcessingTypeDataProvider[Y] {

      override def forType(typ: ProcessingType): Option[Y] = ProcessingTypeDataProvider.this.forType(typ).map(fun)

      override def all: Map[ProcessingType, Y] = ProcessingTypeDataProvider.this.all.mapValues(fun)
    }

  }

}

class MapBasedProcessingTypeDataProvider[T](map: Map[ProcessingType, T]) extends ProcessingTypeDataProvider[T] {

  override def forType(typ: ProcessingType): Option[T] = map.get(typ)

  override def all: Map[ProcessingType, T] = map

}

