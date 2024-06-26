package pl.touk.nussknacker.engine.api.process

sealed trait TopicName

object TopicName {
  final case class OfSource(name: String) extends TopicName
  final case class OfSink(name: String)   extends TopicName

}
