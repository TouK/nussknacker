import React from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../reducers/selectors/other"
import ProcessHistory from "../ProcessHistory"
import {CollapsibleToolbar} from "../toolbarComponents/CollapsibleToolbar"

export function VersionsPanel(): JSX.Element {
  const {t} = useTranslation()
  const capabilities = useSelector(getCapabilities)

  return (
    <CollapsibleToolbar id="VERSIONS-PANEL" title={t("panels.versions.title", "Versions")}>
      <ProcessHistory isReadOnly={!capabilities.write}/>
    </CollapsibleToolbar>
  )
}
