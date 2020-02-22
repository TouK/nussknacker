/* eslint-disable i18next/no-literal-string */
import React from "react"
import {OwnProps as PanelOwnProps} from "../../../UserRightPanel"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import {cutSelection} from "../../../../../actions/nk/selection"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type OwnPropsPick = Pick<PanelOwnProps,
  | "selectionActions">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function CutButton(props: Props) {
  const {selectionActions, cutSelection} = props
  const {cut, canCut} = selectionActions

  return (
    <ButtonWithIcon
      name={"cut"}
      icon={"cut.svg"}
      disabled={!canCut}
      onClick={event => cutSelection(
        () => cut(event),
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      )}
    />
  )
}

const mapDispatch = {
  cutSelection,
}

type StateProps = typeof mapDispatch

export default connect(null, mapDispatch)(CutButton)
