/* eslint-disable i18next/no-literal-string */
import {PanelConfig} from "../PanelConfig"
import React from "react"
import {ExtractedPanel} from "../ExtractedPanel"
import {OwnProps as PanelOwnProps} from "../UserRightPanel"
import {RootState} from "../../../reducers/index"
import ProcessUtils from "../../../common/ProcessUtils"
import {connect} from "react-redux"
import {EspActionsProps, mapDispatchWithEspActions} from "../../../actions/ActionsUtils"
import {events} from "../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import NodeUtils from "../../graph/NodeUtils"
import {isEmpty} from "lodash"
import {getProcessToDisplay, isPristine, isSubprocess, isBusinessView, getHistory, getSelectionState, getNodeToDisplay} from "../selectors"
import {areAllModalsClosed} from "../selectors-ui"

type OwnPropsPick = Pick<PanelOwnProps,
  | "capabilities"
  | "graphLayoutFunction"
  | "isStateLoaded"
  | "selectionActions"
  | "processState">

export function getConfig(own: OwnPropsPick, props: StateProps): PanelConfig[] {
  const {capabilities, graphLayoutFunction, selectionActions} = own
  const {isSubprocess, hasErrors, processToDisplay, nodeToDisplay, history, keyActionsAvailable, selectionState} = props
  const {copySelection, cutSelection, deleteSelection, displayModalNodeDetails, layout, pasteSelection} = props.actions
  const {redo: redo1, undo: undo1} = props.undoRedoActions

  const undo = () => keyActionsAvailable && undo1(
    {category: events.categories.rightPanel, action: events.actions.buttonClick},
  )

  const redo = () => keyActionsAvailable && redo1(
    {category: events.categories.rightPanel, action: events.actions.buttonClick},
  )

  const showProperties = () => {
    displayModalNodeDetails(
      processToDisplay?.properties,
      undefined,
      {
        category: events.categories.rightPanel,
        name: "properties",
      },
    )
  }

  const propertiesBtnClass = hasErrors && !ProcessUtils.hasNoPropertiesErrors(processToDisplay) ? "esp-button-error right-panel" : null

  const editPanel = {
    panelName: "Edit",
    buttons: [
      {
        name: "undo",
        isHidden: !capabilities.write,
        disabled: history.past.length === 0,
        onClick: undo,
        icon: InlinedSvgs.buttonUndo,
      },
      {
        name: "redo",
        isHidden: !capabilities.write,
        disabled: history.future.length === 0,
        onClick: redo,
        icon: InlinedSvgs.buttonRedo,
      },
      {
        name: "layout",
        onClick: () => layout(graphLayoutFunction),
        icon: InlinedSvgs.buttonLayout,
        isHidden: !capabilities.write,
      },
      {
        name: "properties",
        className: propertiesBtnClass,
        onClick: showProperties,
        icon: InlinedSvgs.buttonSettings,
        isHidden: isSubprocess,
      },
      {
        name: "copy",
        onClick: (event) => copySelection(
          () => selectionActions.copy(event),
          {category: events.categories.rightPanel, action: events.actions.buttonClick},
        ),
        icon: "copy.svg",
        isHidden: !capabilities.write,
        disabled: !selectionActions.canCopy,
      },
      {
        name: "cut",
        onClick: (event) => cutSelection(
          () => selectionActions.cut(event),
          {category: events.categories.rightPanel, action: events.actions.buttonClick},
        ),
        icon: "cut.svg",
        isHidden: !capabilities.write,
        disabled: !selectionActions.canCut,
      },
      {
        name: "delete",
        onClick: () => deleteSelection(
          selectionState,
          {category: events.categories.rightPanel, action: events.actions.buttonClick},
        ),
        icon: "delete.svg",
        isHidden: !capabilities.write,
        disabled: !NodeUtils.isPlainNode(nodeToDisplay) || isEmpty(selectionState),
      },
      {
        name: "paste",
        onClick: (event) => pasteSelection(
          () => selectionActions.paste(event),
          {category: events.categories.rightPanel, action: events.actions.buttonClick},
        ),
        icon: "paste.svg",
        isHidden: !capabilities.write,
        disabled: !selectionActions.canPaste,
      },
    ],
  }

  return [editPanel]
}

type OwnProps = OwnPropsPick

export function EditPanel(props: Props) {
  const panelConfigs = getConfig(props, props)

  return (
    <>
      {panelConfigs.map((panel) => <ExtractedPanel {...panel} key={panel.panelName}/>)}
    </>
  )
}

const mapState = (state: RootState) => ({
  keyActionsAvailable: areAllModalsClosed(state),
  processToDisplay: getProcessToDisplay(state),
  nodeToDisplay: getNodeToDisplay(state),
  selectionState: getSelectionState(state),
  isSubprocess: isSubprocess(state),
  businessView: isBusinessView(state),
  history: getHistory(state),
  hasErrors: isPristine(state),
})

export type StateProps = EspActionsProps & ReturnType<typeof mapState>
export type Props = OwnProps & StateProps

export default connect(mapState, mapDispatchWithEspActions)(EditPanel)
