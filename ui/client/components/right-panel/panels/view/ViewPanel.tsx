import React, {memo} from "react"
import {useTranslation} from "react-i18next"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import BussinesViewSwitch from "./BussinesViewSwitch"
import {ToolbarButtons} from "../../../Process/ToolbarButtons"
import {ResetViewButton} from "./ResetViewButton"
import {ZoomInButton} from "./ZoomInButton"
import {ZoomOutButton} from "./ZoomOutButton"

function ViewPanel() {
  const {t} = useTranslation()
  return (
    <CollapsibleToolbar id="VIEW-PANEL" title={t("panels.view.title", "view")}>
      <ToolbarButtons>
        <BussinesViewSwitch/>
        <ResetViewButton/>
        <ZoomInButton/>
        <ZoomOutButton/>
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

export default memo(ViewPanel)

