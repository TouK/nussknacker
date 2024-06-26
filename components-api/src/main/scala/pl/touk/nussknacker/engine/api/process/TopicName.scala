package pl.touk.nussknacker.engine.api.process

import cats.Show

sealed trait TopicName

object TopicName {
  case class OfSource(name: String) extends TopicName
  case class OfSink(name: String)   extends TopicName

  implicit val show: Show[TopicName] = Show.show {
    case OfSource(name) => name
    case OfSink(name)   => name
  }

}
