import "ladda/dist/ladda.min.css"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import Draggable from "react-draggable"
import LaddaButton from "react-ladda"
import Modal from "react-modal"
import {useDispatch, useSelector} from "react-redux"
import {closeModals, editEdge} from "../../../actions/nk"
import {isEdgeEditable} from "../../../common/EdgeUtils"
import {getEdgeToDisplay, getProcessToDisplay} from "../../../reducers/selectors/graph"
import {getCapabilities} from "../../../reducers/selectors/other"
import {isEdgeDetailsModalVisible} from "../../../reducers/selectors/ui"
import {ButtonWithFocus} from "../../withFocus"
import {Details} from "./edge/Details"
import {EdgeDetailsModalHeader} from "./edge/EdgeDetailsModalHeader"

//TODO: this is still pretty switch-specific.
function EdgeDetailsModal(): JSX.Element {
  const edge = useSelector(getEdgeToDisplay)
  const processToDisplay = useSelector(getProcessToDisplay)
  const {write} = useSelector(getCapabilities)
  const showModal = useSelector(isEdgeDetailsModalVisible)

  const readOnly = !write

  const dispatch = useDispatch()

  const [editedEdge, setEditedEdge] = useState(edge)
  const [pending, setPending] = useState(false)

  const isOpen = useMemo(() => isEdgeEditable(edge) && showModal, [edge, showModal])

  useEffect(() => {
    isOpen && setEditedEdge(edge)
  }, [edge, isOpen])

  const closeModal = useCallback(() => dispatch(closeModals()), [dispatch])

  const performEdgeEdit = useCallback(async () => {
    setPending(true)
    await dispatch(editEdge(processToDisplay, edge, editedEdge))
    setPending(false)
    closeModal()
  }, [closeModal, dispatch, edge, processToDisplay, editedEdge])

  const updateEdgeProp = useCallback((prop, value) => {
    const editedEdge = cloneDeep(state.editedEdge)
    const newEdge = set(editedEdge, prop, value)
    setState(s => ({...s, editedEdge: newEdge}))
  }, [state.editedEdge])

  const changeEdgeTypeValue = useCallback((edgeTypeValue: EdgeType) => {
    const fromNode = NodeUtils.getNodeById(edge.from, processToDisplay)
    const defaultEdgeType = NodeUtils
      .edgesForNode(fromNode, processDefinitionData).edges.find(e => e.type === edgeTypeValue)
    const newEdge = {
      ...state.editedEdge,
      edgeType: {
        ...defaultEdgeType,
        //we want to preserve previously edited (in state) value of expression condition
        condition: state.editedEdge.edgeType.condition,
      },
    }
    setState(s => ({...s, editedEdge: newEdge}))
  }, [edge.from, processDefinitionData, processToDisplay])

  const renderModalButtons = useCallback(() => {
    return [
      <ButtonWithFocus key="2" type="button" title="Cancel node details" className="modalButton" onClick={closeModal}>
        Cancel
      </ButtonWithFocus>,
      !readOnly ?
        (
          <LaddaButton
            key="1"
            title="Apply edge details"
            className="modalButton pull-right modalConfirmButton"
            loading={pending}
            data-style="zoom-in"
            onClick={performEdgeEdit}
          >
            Apply
          </LaddaButton>
        ) :
        null,
    ]
  }, [closeModal, pending, performEdgeEdit, readOnly])

  return (
    <div className="objectModal">
      <Modal
        isOpen={isOpen}
        shouldCloseOnOverlayClick={false}
        onRequestClose={closeModal}
      >
        <div className="draggable-container">
          <Draggable bounds="parent" handle=".modal-draggable-handle">
            <div className="espModal">
              <EdgeDetailsModalHeader />
              <div className="modalContentDark edge-details">
                <Details
                  edge={editedEdge}
                  onChange={setEditedEdge}
                  processToDisplay={processToDisplay}
                />
              </div>
              <div className="modalFooter">
                <div className="footerButtons">
                  {renderModalButtons()}
                </div>
              </div>
            </div>
          </Draggable>
        </div>
      </Modal>
    </div>
  )
}

export default EdgeDetailsModal
