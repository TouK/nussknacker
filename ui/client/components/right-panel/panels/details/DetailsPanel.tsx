import React from "react"
import {useTranslation} from "react-i18next"
import SideNodeDetails from "./SideNodeDetails"
import {RightPanel} from "../RightPanel"

export type Props = {
  showDetails: boolean,
}

export function DetailsPanel(props: Props) {
  const {showDetails} = props
  const {t} = useTranslation()

  return (
    <RightPanel title={t("panels.details.title", "Details")} isHidden={!showDetails}>
      <SideNodeDetails/>
    </RightPanel>
  )
}

export default DetailsPanel
