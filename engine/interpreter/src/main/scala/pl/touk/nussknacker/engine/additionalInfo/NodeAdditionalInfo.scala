package pl.touk.nussknacker.engine.additionalInfo

import io.circe.derivation.annotations.JsonCodec

/**
 * This trait represents additional information which can be presented for each node
 * To see usage please check NodeAdditionalInfoProvider trait and NodeAdditionalInfoBox component
 * Each type of info (i.e. implementation of this trait) must also be handled in NodeAdditionalInfoBox.ts!
 */
@JsonCodec sealed trait NodeAdditionalInfo

/**
 *  Contents will be rendered via https://github.com/rexxars/react-markdown component
 */
@JsonCodec case class MarkdownNodeAdditionalInfo(content: String) extends NodeAdditionalInfo