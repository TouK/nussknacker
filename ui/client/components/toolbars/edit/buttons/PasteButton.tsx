import React from "react"
import {useDispatch} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {pasteSelection} from "../../../../actions/nk/selection"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {useTranslation} from "react-i18next"
import {useSelectionActions} from "../../../graph/GraphContext"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/paste.svg"

function PasteButton(): JSX.Element {
  const dispatch = useDispatch()
  const {t} = useTranslation()

  const {canPaste, paste} = useSelectionActions()
  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.edit-paste.button", "paste")}
      icon={<Icon/>}
      disabled={!canPaste}
      onClick={event => dispatch(pasteSelection(
        () => paste(event),
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      ))}
    />
  )
}

export default PasteButton
