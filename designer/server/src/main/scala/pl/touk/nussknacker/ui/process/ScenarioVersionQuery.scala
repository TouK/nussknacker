package pl.touk.nussknacker.ui.process

final case class ScenarioVersionQuery(
    excludedUserNames: Option[Seq[String]] = None,
)
