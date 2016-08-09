package pl.touk.esp.engine.api

import java.util

trait FoldingFunction[Accumulator] extends Serializable {

  def fold(value: AnyRef, acc: Option[Accumulator]) : Accumulator

}

object JListFoldingFunction extends FoldingFunction[java.util.List[AnyRef]] {

  //TODO: na ile to jest bezpieczne??
  override def fold(value: AnyRef, acc: Option[util.List[AnyRef]]) = {
    val accOrEmpty = acc.getOrElse(new util.ArrayList[AnyRef]())
    accOrEmpty.add(value)
    accOrEmpty
  }
}
