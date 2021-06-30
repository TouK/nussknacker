import {CollapsibleToolbar} from "../toolbarComponents/CollapsibleToolbar"
import ProcessAttachments from "../ProcessAttachments"
import React from "react"
import {useTranslation} from "react-i18next"

export function AttachmentsPanel() {
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="attachments-panel" title={t("panels.attachments.title", "Attachments")}>
      <ProcessAttachments/>
    </CollapsibleToolbar>
  )
}
