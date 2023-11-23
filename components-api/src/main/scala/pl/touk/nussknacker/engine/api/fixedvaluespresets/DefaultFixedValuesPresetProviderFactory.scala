package pl.touk.nussknacker.engine.api.fixedvaluespresets

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class DefaultFixedValuesPresetProviderFactory extends FixedValuesPresetProviderFactory {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  final val configPath: String = "fixedValuesPresets"

  def create(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext,
  ): FixedValuesPresetProvider = {
    val fixedValuesPresets = config
      .getAs[Map[String, List[FixedExpressionValue]]](configPath)
      .getOrElse(Map.empty)

    new DefaultFixedValuesPresetProvider(fixedValuesPresets)
  }

}
