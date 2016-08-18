package pl.touk.esp.engine.perftest.sample

import java.util.Date

import pl.touk.esp.engine.api.FoldingFunction

object model {

  case class KeyValue(key: String, value: Int, date: Date)

  object KeyValue {
    def apply(list: List[String]): KeyValue = {
      KeyValue(list.head, list(1).toInt, new Date(list(2).toLong))
    }

    object sum extends FoldingFunction[Int] {
      override def fold(value: AnyRef, acc: Option[Int]) =
        acc.getOrElse(0) + value.asInstanceOf[KeyValue].value
    }
  }

}
