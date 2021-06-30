import React, {memo} from "react"
import {useTranslation} from "react-i18next"
import SideNodeDetails from "./SideNodeDetails"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../../reducers/selectors/other"

function DetailsPanel() {
  const capabilities = useSelector(getCapabilities)
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="details-panel" title={t("panels.details.title", "Details")} isHidden={!capabilities.write}>
      <SideNodeDetails/>
    </CollapsibleToolbar>
  )
}

export default memo(DetailsPanel)
