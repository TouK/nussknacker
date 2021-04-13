import React from "react"
import {useDispatch} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {copySelection} from "../../../../actions/nk/selection"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {useTranslation} from "react-i18next"
import {useSelectionActions} from "../../../graph/GraphContext"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/copy.svg"

function CopyButton(): JSX.Element {
  const {copy, canCopy} = useSelectionActions()
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.edit-copy.button", "copy")}
      icon={<Icon/>}
      disabled={!canCopy}
      onClick={event => dispatch(copySelection(
        () => copy(event),
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      ))}
    />
  )
}

export default CopyButton

