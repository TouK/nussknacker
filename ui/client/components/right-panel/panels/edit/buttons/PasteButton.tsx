/* eslint-disable i18next/no-literal-string */
import React from "react"
import {OwnProps as PanelOwnProps} from "../../../UserRightPanel"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import {pasteSelection} from "../../../../../actions/nk/selection"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type OwnPropsPick = Pick<PanelOwnProps,
  | "selectionActions">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function PasteButton(props: Props) {
  const {selectionActions, pasteSelection} = props

  const {canPaste, paste} = selectionActions
  return (
    <ButtonWithIcon
      name={"paste"}
      icon={"paste.svg"}
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
