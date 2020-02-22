/* eslint-disable i18next/no-literal-string */
import React from "react"
import {OwnProps as PanelOwnProps} from "../../../UserRightPanel"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import NodeUtils from "../../../../graph/NodeUtils"
import {isEmpty} from "lodash"
import {getSelectionState, getNodeToDisplay} from "../../../selectors"
import {deleteSelection} from "../../../../../actions/nk/selection"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type OwnPropsPick = Pick<PanelOwnProps,
  | "selectionActions">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function DeleteButton(props: Props) {
  const {nodeToDisplay, selectionState, deleteSelection} = props

  return (
    <ButtonWithIcon
      name={"delete"}
      icon={"delete.svg"}
      disabled={!NodeUtils.isPlainNode(nodeToDisplay) || isEmpty(selectionState)}
      onClick={() => deleteSelection(
        selectionState,
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      )}
    />
  )
}

const mapState = (state: RootState) => ({
  nodeToDisplay: getNodeToDisplay(state),
  selectionState: getSelectionState(state),
})

const mapDispatch = {
  deleteSelection,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(DeleteButton)
