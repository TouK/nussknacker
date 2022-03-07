package pl.touk.nussknacker.ui.listener

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.listener.services.NussknackerServices

trait ProcessChangeListenerFactory {
  def create(config: Config, services: NussknackerServices): ProcessChangeListener
}
