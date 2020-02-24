import React from "react"
import {useTranslation} from "react-i18next"
import {RightPanel} from "../RightPanel"
import BussinesViewSwitch from "./BussinesViewSwitch"

function ViewPanel() {
  const {t} = useTranslation()

  return (
    <RightPanel title={t("panels.view.title", "view")}>
      <BussinesViewSwitch/>
    </RightPanel>
  )
}

export default ViewPanel
