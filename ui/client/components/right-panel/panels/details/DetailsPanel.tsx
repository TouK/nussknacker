import React from "react"
import {useTranslation} from "react-i18next"
import SideNodeDetails from "./SideNodeDetails"
import {RightToolPanel} from "../RightToolPanel"

export type Props = {
  showDetails: boolean,
}

export function DetailsPanel(props: Props) {
  const {showDetails} = props
  const {t} = useTranslation()

  return (
    <RightToolPanel title={t("panels.details.title", "Details")} isHidden={!showDetails}>
      <SideNodeDetails/>
    </RightToolPanel>
  )
}

export default DetailsPanel
