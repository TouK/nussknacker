import {CollapsibleToolbar} from "../toolbarComponents/CollapsibleToolbar"
import ToolBox from "../ToolBox"
import React from "react"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../reducers/selectors/other"
import {useTranslation} from "react-i18next"

export function CreatorPanel() {
  const capabilities = useSelector(getCapabilities)
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="CREATOR-PANEL" title={t("panels.creator.title", "Creator panel")} isHidden={!capabilities.write}>
      <ToolBox/>
    </CollapsibleToolbar>
  )
}
