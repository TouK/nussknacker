import {CollapsibleToolbar} from "../CollapsibleToolbar"
import ProcessHistory from "../../../ProcessHistory"
import React from "react"
import {useTranslation} from "react-i18next"

export function VersionsPanel() {
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="VERSIONS-PANEL" title={t("panels.versions.title", "Versions")}>
      <ProcessHistory/>
    </CollapsibleToolbar>
  )
}
