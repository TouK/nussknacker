import "ladda/dist/ladda.min.css"
import {cloneDeep, set} from "lodash"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import Draggable from "react-draggable"
import LaddaButton from "react-ladda"
import Modal from "react-modal"
import {useDispatch, useSelector} from "react-redux"
import {closeModals, editEdge} from "../../../actions/nk"
import {isEdgeEditable} from "../../../common/EdgeUtils"
import NkModalStyles from "../../../common/NkModalStyles"
import ProcessUtils from "../../../common/ProcessUtils"
import {getEdgeToDisplay, getProcessCategory, getProcessToDisplay} from "../../../reducers/selectors/graph"
import {getCapabilities} from "../../../reducers/selectors/other"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {isEdgeDetailsModalVisible} from "../../../reducers/selectors/ui"
import {EdgeType} from "../../../types"
import {ButtonWithFocus} from "../../withFocus"
import NodeUtils from "../NodeUtils"
import EdgeDetailsContent from "./EdgeDetailsContent"

//TODO: this is still pretty switch-specific.
function EdgeDetailsModal(): JSX.Element {
  const edge = useSelector(getEdgeToDisplay)
  const processToDisplay = useSelector(getProcessToDisplay)
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const processCategory = useSelector(getProcessCategory)
  const {write} = useSelector(getCapabilities)
  const showModal = useSelector(isEdgeDetailsModalVisible)

  const nodeId = edge.from
  const readOnly = !write

  const variableTypes = useMemo(() => {
    const findAvailableVariables = ProcessUtils.findAvailableVariables(
      processDefinitionData,
      processCategory,
      processToDisplay,
    )
    return findAvailableVariables(nodeId, undefined)
  }, [nodeId, processCategory, processDefinitionData, processToDisplay])

  const dispatch = useDispatch()

  const [state, setState] = useState({
    pendingRequest: false,
    editedEdge: edge,
  })

  useEffect(() => {
    setState(s => ({...s, editedEdge: edge}))
  }, [edge])

  const closeModal = useCallback(() => dispatch(closeModals()), [dispatch])

  const performEdgeEdit = useCallback(async () => {
    setState(s => ({...s, pendingRequest: true}))
    await dispatch(editEdge(processToDisplay, edge, state.editedEdge))
    setState(s => ({...s, pendingRequest: false}))
    closeModal()
  }, [closeModal, dispatch, edge, processToDisplay, state.editedEdge])

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
      ...this.state.editedEdge,
      edgeType: defaultEdgeType,
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
            loading={state.pendingRequest}
            data-style="zoom-in"
            onClick={performEdgeEdit}
          >
            Apply
          </LaddaButton>
        ) :
        null,
    ]
  }, [closeModal, performEdgeEdit, readOnly, state.pendingRequest])

  const isOpen = useMemo(() => isEdgeEditable(edge) && showModal, [edge, showModal])
  const titleStyles = NkModalStyles.headerStyles("#2D8E54", "white")

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
              <div className="modalHeader">
                <div className="edge-modal-title modal-draggable-handle" style={titleStyles}>
                  <span>edge</span>
                </div>
              </div>
              <div className="modalContentDark edge-details">
                <EdgeDetailsContent
                  changeEdgeTypeValue={changeEdgeTypeValue}
                  updateEdgeProp={updateEdgeProp}
                  readOnly={readOnly}
                  edge={state.editedEdge}
                  showValidation={true}
                  showSwitch={true}
                  variableTypes={variableTypes}
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
