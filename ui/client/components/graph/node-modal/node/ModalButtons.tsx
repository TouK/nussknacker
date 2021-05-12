import React from "react"
import {useDispatch, useSelector} from "react-redux"
import {collapseGroup, expandGroup} from "../../../../actions/nk"
import {getExpandedGroups} from "../../../../reducers/selectors/groups"
import {LaddaButton} from "../../../../windowManager/LaddaButton"
import {ButtonWithFocus} from "../../../withFocus"
import NodeUtils from "../../NodeUtils"

export function ModalButtons({
  editedNode,
  readOnly,
  pendingRequest,
  closeModal,
  performNodeEdit,
}): JSX.Element {
  const expandedGroups = useSelector(getExpandedGroups)
  const dispatch = useDispatch()

  const renderGroupUngroup = () => {
    const {id} = editedNode
    const expanded = expandedGroups.includes(id)
    return (
      <ButtonWithFocus
        title={expanded ? "Collapse group" : "Expand group"}
        type="button"
        className="modalButton"
        onClick={() => {
          dispatch(expanded ? collapseGroup(id) : expandGroup(id))
          closeModal()
        }}
      >
        {expanded ? "Collapse" : "Expand"}
      </ButtonWithFocus>
    )
  }

  return (
    <>
      {NodeUtils.nodeIsGroup(editedNode) ? renderGroupUngroup() : null}
      <ButtonWithFocus key="2" type="button" title="Cancel node details" className="modalButton" onClick={closeModal}>
        Cancel
      </ButtonWithFocus>
      {!readOnly ?
        (
          <LaddaButton
            key="1"
            title="Apply node details"
            className="modalButton pull-right modalConfirmButton"
            loading={pendingRequest}
            data-style="zoom-in"
            onClick={performNodeEdit}
          >
            Apply
          </LaddaButton>
        ) :
        null}
    </>
  )
}
