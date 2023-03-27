package pl.touk.nussknacker.engine.api.deployment

import java.net.URI

/**
  * It is used to specify:
  * <ul>
  * <li>fixed default properties of a status: icon, tooltip, descripition
  * <li>fixed set of properties of filtering options: displayableName, icon tooltip
  * </ul>
  * When a status has dynamic properties use ProcessStateDefinitionManager to handle them.
  *
  * @see default values of a status in [[ProcessStateDefinitionManager]]
  * @see filtering options in [[UIStateDefinition]]
  * @see overriding state definitions in [[OverridingProcessStateDefinitionManager]]
  */
case class StateDefinitionDetails(displayableName: String,
                                  icon: URI,
                                  tooltip: String,
                                  description: String)

object StateDefinitionDetails {
  val UnknownIcon: URI = URI.create("/assets/states/status-unknown.svg")
}