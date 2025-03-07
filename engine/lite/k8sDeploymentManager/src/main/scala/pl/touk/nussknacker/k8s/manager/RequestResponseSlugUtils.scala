package pl.touk.nussknacker.k8s.manager

import pl.touk.nussknacker.engine.api.RequestResponseMetaData
import pl.touk.nussknacker.engine.api.process.ProcessName

object RequestResponseSlugUtils {

  private[manager] def determineSlug(
      scenarioName: ProcessName,
      rrMetaData: RequestResponseMetaData,
      nussknackerInstanceName: OptionalNussknackerInstanceName
  ) = {
    rrMetaData.slug.filterNot(_.isEmpty).getOrElse(defaultSlug(scenarioName, nussknackerInstanceName))
  }

  // We don't encode url because k8s object names are more restrictively validated than urls, see https://datatracker.ietf.org/doc/html/rfc3986
  // and all invalid characters will be clean
  private[manager] def defaultSlug(
      scenarioName: ProcessName,
      nussknackerInstanceName: OptionalNussknackerInstanceName
  ): String = {
    val maxSlugLength =
      K8sUtils.maxObjectNameLength - nussknackerInstanceName.instanceNamePrefix.length
    K8sUtils
      .sanitizeObjectName(
        scenarioName.value,
        firstCharacterShouldBeAlphaNumer = false, // because we will add instance name in final service name
        lastCharacterShouldBeAlphaNumer = true,
        maxSlugLength
      )
  }

}
