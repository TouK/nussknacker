import React from "react"
import {useDispatch, useSelector} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import NodeUtils from "../../../graph/NodeUtils"
import {isEmpty} from "lodash"
import {deleteSelection} from "../../../../actions/nk/selection"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getSelectionState, getNodeToDisplay} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/delete.svg"

function DeleteButton(): JSX.Element {
  const nodeToDisplay = useSelector(getNodeToDisplay)
  const selectionState = useSelector(getSelectionState)
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.edit-delete.button", "delete")}
      icon={<Icon/>}
      disabled={!NodeUtils.isPlainNode(nodeToDisplay) || isEmpty(selectionState)}
      onClick={() => dispatch(deleteSelection(
        selectionState,
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      ))}
    />
  )
}

export default DeleteButton
