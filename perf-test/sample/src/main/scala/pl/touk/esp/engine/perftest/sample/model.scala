package pl.touk.esp.engine.perftest.sample

import java.util.Date

object model {

  case class KeyValue(key: String, value: Int, date: Date)

  object KeyValue {
    def apply(list: List[String]): KeyValue = {
      KeyValue(list.head, list(1).toInt, new Date(list(2).toLong))
    }
  }

}
