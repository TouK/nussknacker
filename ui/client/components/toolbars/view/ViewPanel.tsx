import React, {memo} from "react"
import {useTranslation} from "react-i18next"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import BussinesViewSwitch from "./buttons/BussinesViewSwitch"
import {ToolbarButtons} from "../../toolbarComponents/ToolbarButtons"
import {ResetViewButton} from "./buttons/ResetViewButton"
import {ZoomInButton} from "./buttons/ZoomInButton"
import {ZoomOutButton} from "./buttons/ZoomOutButton"

function ViewPanel() {
  const {t} = useTranslation()
  return (
    <CollapsibleToolbar id="VIEW-PANEL" title={t("panels.view.title", "view")}>
      <ToolbarButtons>
        <BussinesViewSwitch/>
        <ZoomInButton/>
        <ZoomOutButton/>
        <ResetViewButton/>
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

export default memo(ViewPanel)

