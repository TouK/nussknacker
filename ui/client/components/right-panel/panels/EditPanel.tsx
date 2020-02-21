/* eslint-disable i18next/no-literal-string */
import React from "react"
import {ExtractedPanel} from "../ExtractedPanel"
import {OwnProps as PanelOwnProps} from "../UserRightPanel"
import {RootState} from "../../../reducers/index"
import ProcessUtils from "../../../common/ProcessUtils"
import {connect} from "react-redux"
import {events} from "../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import NodeUtils from "../../graph/NodeUtils"
import {isEmpty} from "lodash"
import {getProcessToDisplay, isPristine, isSubprocess, isBusinessView, getHistory, getSelectionState, getNodeToDisplay} from "../selectors"
import {areAllModalsClosed} from "../selectors-ui"
import {copySelection, cutSelection, deleteSelection, pasteSelection} from "../../../actions/nk/selection"
import {displayModalNodeDetails} from "../../../actions/nk/modal"
import {layout} from "../../../actions/nk/ui/layout"
import {undo, redo} from "../../../actions/undoRedoActions"

type OwnPropsPick = Pick<PanelOwnProps,
  | "capabilities"
  | "graphLayoutFunction"
  | "selectionActions">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function EditPanel(props: Props) {
  const {capabilities, graphLayoutFunction, selectionActions} = props
  const {isSubprocess, hasErrors, processToDisplay, nodeToDisplay, history, keyActionsAvailable, selectionState} = props
  const {copySelection, cutSelection, deleteSelection, displayModalNodeDetails, layout, pasteSelection, redo, undo} = props

  const propertiesBtnClass = hasErrors && !ProcessUtils.hasNoPropertiesErrors(processToDisplay) ? "esp-button-error right-panel" : null

  const buttons = [
    {
      name: "undo",
      isHidden: !capabilities.write,
      disabled: history.past.length === 0,
      onClick: () => keyActionsAvailable && undo({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
      }),
      icon: InlinedSvgs.buttonUndo,
    },
    {
      name: "redo",
      isHidden: !capabilities.write,
      disabled: history.future.length === 0,
      onClick: () => keyActionsAvailable && redo({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
      }),
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
      onClick: () => displayModalNodeDetails(
        processToDisplay?.properties,
        undefined,
        {
          category: events.categories.rightPanel,
          name: "properties",
        },
      ),
      icon: InlinedSvgs.buttonSettings,
      isHidden: isSubprocess,
    },
    {
      name: "copy",
      onClick: event => copySelection(
        () => selectionActions.copy(event),
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      ),
      icon: "copy.svg",
      isHidden: !capabilities.write,
      disabled: !selectionActions.canCopy,
    },
    {
      name: "cut",
      onClick: event => cutSelection(
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
      onClick: event => pasteSelection(
        () => selectionActions.paste(event),
        {category: events.categories.rightPanel, action: events.actions.buttonClick},
      ),
      icon: "paste.svg",
      isHidden: !capabilities.write,
      disabled: !selectionActions.canPaste,
    },
  ]

  return (
    <ExtractedPanel panelName={"Edit"} buttons={buttons}/>
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

const mapDispatch = {
  copySelection,
  cutSelection,
  deleteSelection,
  displayModalNodeDetails,
  layout,
  pasteSelection,
  redo,
  undo,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(EditPanel)
