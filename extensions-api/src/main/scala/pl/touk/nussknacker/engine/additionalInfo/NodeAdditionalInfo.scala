package pl.touk.nussknacker.engine.additionalInfo

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._

/**
 * This trait represents additional information which can be presented for each node
 * To see usage please check NodeAdditionalInfoProvider trait and NodeAdditionalInfoBox component
 * Each type of info (i.e. implementation of this trait) must also be handled in NodeAdditionalInfoBox.ts!
 */
@ConfiguredJsonCodec sealed trait NodeAdditionalInfo

/**
 *  Contents will be rendered via https://github.com/rexxars/react-markdown component
 */
case class MarkdownNodeAdditionalInfo(content: String) extends NodeAdditionalInfo