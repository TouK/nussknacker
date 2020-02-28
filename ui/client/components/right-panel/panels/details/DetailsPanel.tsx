import React, {memo} from "react"
import {useTranslation} from "react-i18next"
import SideNodeDetails from "./SideNodeDetails"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"

export type Props = {
  showDetails: boolean,
}

function DetailsPanel(props: Props) {
  const {showDetails} = props
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="DETAILS-PANEL" title={t("panels.details.title", "Details")} isHidden={!showDetails}>
      <SideNodeDetails/>
    </CollapsibleToolbar>
  )
}

export default memo(DetailsPanel)
