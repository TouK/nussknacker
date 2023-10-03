package pl.touk.nussknacker.k8s.manager

import pl.touk.nussknacker.engine.api.deployment.OverridingProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager

object K8sProcessStateDefinitionManager
    extends OverridingProcessStateDefinitionManager(
      delegate = SimpleProcessStateDefinitionManager
    )
