import React, {memo, FC} from "react"
import {useTranslation} from "react-i18next"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import BussinesViewSwitch from "./BussinesViewSwitch"

function ViewPanel() {
  const {t} = useTranslation()
  return (
    <CollapsibleToolbar id="VIEW-PANEL" title={t("panels.view.title", "view")}>
      <BussinesViewSwitch/>
    </CollapsibleToolbar>
  )
}

export default memo(ViewPanel)
