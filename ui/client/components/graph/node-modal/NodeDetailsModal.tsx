/* eslint-disable i18next/no-literal-string */
import _ from "lodash"
import React, {useEffect, useState} from "react"
import Modal from "react-modal"
import {useDispatch, useSelector} from "react-redux"
import {closeModals, editGroup, editNode} from "../../../actions/nk"
import {getNodeToDisplay, getProcessToDisplay} from "../../../reducers/selectors/graph"
import {isNodeDetailsModalVisible} from "../../../reducers/selectors/ui"
import NodeUtils from "../NodeUtils"
import {ChildrenElement} from "./node/ChildrenElement"
import {ContentWrapper} from "./node/ContentWrapper"
import {ModalButtons} from "./node/ModalButtons"
import {ModalContentWrapper} from "./node/ModalContentWrapper"
import {getNodeSettings, getReadOnly} from "./node/selectors"
import {SubprocessContent} from "./node/SubprocessContent"
import NodeDetailsModalHeader from "./NodeDetailsModalHeader"

const NodeDetailsModal = () => {
  const processToDisplay = useSelector(getProcessToDisplay)
  const nodeToDisplay = useSelector(getNodeToDisplay)
  const readOnly = useSelector(getReadOnly)
  const showNodeDetailsModal = useSelector(isNodeDetailsModalVisible)

  const [editedNode, setEditedNode] = useState(nodeToDisplay)
  const [pendingRequest, setPendingRequest] = useState(false)

  useEffect(
    () => {
      setEditedNode(nodeToDisplay)
    },
    [nodeToDisplay],
  )

  const dispatch = useDispatch()

  const closeModal = () => {
    dispatch(closeModals())
  }

  const performNodeEdit = () => {
    setPendingRequest(true)

    const action = NodeUtils.nodeIsGroup(editedNode) ?
      //TODO: try to get rid of this.state.editedNode, passing state of NodeDetailsContent via onChange is not nice...
      editGroup(processToDisplay, nodeToDisplay.id, editedNode) :
      editNode(processToDisplay, nodeToDisplay, editedNode)

    Promise.all([dispatch(action)]).then(
      () => {
        setPendingRequest(false)
        closeModal()
      },
      () => setPendingRequest(false),
    )
  }

  const isOpen = !_.isEmpty(nodeToDisplay) && showNodeDetailsModal

  return (
    <div className="objectModal">
      <Modal
        shouldCloseOnOverlayClick={false}
        isOpen={isOpen}
        onRequestClose={closeModal}
        shouldCloseOnEsc
      >
        <ModalContentWrapper>
          <NodeDetailsModalHeader node={nodeToDisplay}/>
          <ContentWrapper>
            <ChildrenElement
              editedNode={editedNode}
              readOnly={readOnly}

              currentNodeId={nodeToDisplay.id}
              updateNodeState={setEditedNode}
            >
              {NodeUtils.nodeIsSubprocess(nodeToDisplay) && (
                <SubprocessContent nodeToDisplay={nodeToDisplay} currentNodeId={nodeToDisplay.id}/>
              )}
            </ChildrenElement>
          </ContentWrapper>
          <div className="modalFooter">
            <div className="footerButtons">
              <ModalButtons
                editedNode={editedNode}
                readOnly={readOnly}

                pendingRequest={pendingRequest}
                closeModal={closeModal}
                performNodeEdit={performNodeEdit}
              />
            </div>
          </div>
        </ModalContentWrapper>
      </Modal>
    </div>
  )
}

export default NodeDetailsModal
