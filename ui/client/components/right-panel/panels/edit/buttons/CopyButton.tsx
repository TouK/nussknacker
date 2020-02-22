/* eslint-disable i18next/no-literal-string */
import React from "react"
import {OwnProps as PanelOwnProps} from "../../../UserRightPanel"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import {copySelection} from "../../../../../actions/nk/selection"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type OwnPropsPick = Pick<PanelOwnProps,
  | "selectionActions">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function CopyButton(props: Props) {
  const {selectionActions, copySelection} = props
  const {copy, canCopy} = selectionActions

  return (
    <ButtonWithIcon
      name={"copy"}
      icon={"copy.svg"}
      disabled={!canCopy}
      onClick={event => copySelection(
        () => copy(event),
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      )}
    />
  )
}

const mapDispatch = {
  copySelection,
}

type StateProps = typeof mapDispatch

export default connect(null, mapDispatch)(CopyButton)
