import {Panel} from "react-bootstrap"
import React from "react"
import {useTranslation} from "react-i18next"
import SideNodeDetails from "./SideNodeDetails"

export type Props = {
  showDetails?: boolean,
}

export function Panels(props: Props) {
  const {showDetails} = props
  const {t} = useTranslation()

  return (
    <>
      {/*TODO remove SideNodeDetails? turn out to be not useful*/}
      {showDetails ? (
        <Panel defaultExpanded>
          <Panel.Heading><Panel.Title toggle>{t("panels.details.title", "Details")}</Panel.Title></Panel.Heading>
          <Panel.Collapse>
            <Panel.Body>
              <SideNodeDetails/>
            </Panel.Body>
          </Panel.Collapse>
        </Panel>
      ) : null}
    </>
  )
}

export default Panels
