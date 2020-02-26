import React, {memo} from "react"
import {useTranslation} from "react-i18next"
import {RightToolPanel} from "../RightToolPanel"
import BussinesViewSwitch from "./BussinesViewSwitch"

const ViewPanel = () => {
  const {t} = useTranslation()
  return (
    <RightToolPanel title={t("panels.view.title", "view")}>
      <BussinesViewSwitch/>
    </RightToolPanel>
  )
}

export default memo(ViewPanel)
