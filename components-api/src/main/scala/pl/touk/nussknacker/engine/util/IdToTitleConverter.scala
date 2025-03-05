package pl.touk.nussknacker.engine.util

import org.apache.commons.text.WordUtils

// This class is extracted to be used in every context when we have some id and we want to convert to default Title
// in our application. Currently it is used in DM API but in the future it can be moved into common-api
object IdToTitleConverter {

  // We don't support id in camelCase to force some common rules to id naming. In case of id that already is in camelCase
  // we should migrate this to kebab-case or override the default title
  def toTitle(id: String): String = WordUtils.capitalizeFully(id.replace('-', ' '))

}
