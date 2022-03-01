package pl.touk.nussknacker.engine.api.config

import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.util.config.FicusReaders

object ComponentFicusReaders extends FicusReaders {

  implicit val paramEditorReader: ValueReader[ParameterEditor] = forDecoder

  implicit val paramValidatorReader: ValueReader[ParameterValidator] = forDecoder

  implicit val paramConfigReader: ValueReader[AdditionalPropertyConfig] = forDecoder

}
