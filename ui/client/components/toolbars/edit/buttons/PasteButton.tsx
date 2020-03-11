import React from "react"
import {connect} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {pasteSelection} from "../../../../actions/nk/selection"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {useTranslation} from "react-i18next"
import {SelectionActions} from "../EditPanel"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/paste.svg"

type OwnProps = {
  selectionActions: SelectionActions,
}

type Props = OwnProps & StateProps

function PasteButton(props: Props) {
  const {selectionActions, pasteSelection} = props
  const {t} = useTranslation()

  const {canPaste, paste} = selectionActions
  return (
    <ToolbarButton
      name={t("panels.actions.edit-paste.button", "paste")}
      icon={<Icon/>}
      disabled={!canPaste}
      onClick={event => pasteSelection(
        () => paste(event),
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      )}
    />
  )
}

const mapDispatch = {
  pasteSelection,
}

type StateProps = typeof mapDispatch

export default connect(null, mapDispatch)(PasteButton)
