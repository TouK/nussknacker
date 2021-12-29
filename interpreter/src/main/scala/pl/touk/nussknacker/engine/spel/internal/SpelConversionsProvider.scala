package pl.touk.nussknacker.engine.spel.internal

import org.springframework.core.convert.ConversionService
import pl.touk.nussknacker.engine.api.ConversionsProvider

trait SpelConversionsProvider extends ConversionsProvider {

  def getConversionService: ConversionService

}
