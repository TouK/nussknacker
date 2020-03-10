import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import NodeUtils from "../../../../graph/NodeUtils"
import {isEmpty} from "lodash"
import {deleteSelection} from "../../../../../actions/nk/selection"
import {ToolbarButton} from "../../../ToolbarButton"
import {getSelectionState, getNodeToDisplay} from "../../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {SelectionActions} from "../EditPanel"

type OwnProps = {
  selectionActions: SelectionActions,
}

type Props = OwnProps & StateProps

function DeleteButton(props: Props) {
  const {nodeToDisplay, selectionState, deleteSelection} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.edit-delete.button", "delete")}
      icon={"new/delete.svg"}
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
