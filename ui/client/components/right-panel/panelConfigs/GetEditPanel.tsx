/* eslint-disable i18next/no-literal-string */
import {PanelConfig} from "../PanelConfig"
import {events} from "../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import NodeUtils from "../../graph/NodeUtils"
import {OwnProps as PanelOwnProps} from "../UserRightPanel"
import {Props} from "../Panels1"
import ProcessUtils from "../../../common/ProcessUtils"
import {isEmpty} from "lodash"

type OwnProps = Pick<PanelOwnProps & Props,
  | "selectionState"
  | "capabilities"
  | "nodeToDisplay"
  | "history"
  | "graphLayoutFunction"
  | "selectionActions"
  | "actions"
  | "isSubprocess"
  | "processToDisplay"
  | "undoRedoActions"
  | "keyActionsAvailable"
  | "hasErrors">

export function getEditPanel(props: OwnProps): PanelConfig {
  const {
    selectionState,
    capabilities,
    nodeToDisplay,
    history,
    graphLayoutFunction,
    selectionActions,
    actions,
    isSubprocess,
    processToDisplay,
    undoRedoActions,
    keyActionsAvailable,
    hasErrors,
  } = props

  const undo = () => keyActionsAvailable && undoRedoActions.undo(
    {category: events.categories.rightPanel, action: events.actions.buttonClick},
  )

  const redo = () => keyActionsAvailable && undoRedoActions.redo(
    {category: events.categories.rightPanel, action: events.actions.buttonClick},
  )

  const showProperties = () => {
    actions.displayModalNodeDetails(
      processToDisplay.properties,
      undefined,
      {
        category: events.categories.rightPanel,
        name: "properties",
      },
    )
  }

  const propertiesBtnClass = hasErrors && !ProcessUtils.hasNoPropertiesErrors(processToDisplay) ? "esp-button-error right-panel" : null

  return {
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
        onClick: () => actions.layout(graphLayoutFunction),
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
        onClick: (event) => actions.copySelection(
          () => selectionActions.copy(event),
          {category: events.categories.rightPanel, action: events.actions.buttonClick},
        ),
        icon: "copy.svg",
        isHidden: !capabilities.write,
        disabled: !selectionActions.canCopy,
      },
      {
        name: "cut",
        onClick: (event) => actions.cutSelection(
          () => selectionActions.cut(event),
          {category: events.categories.rightPanel, action: events.actions.buttonClick},
        ),
        icon: "cut.svg",
        isHidden: !capabilities.write,
        disabled: !selectionActions.canCut,
      },
      {
        name: "delete",
        onClick: () => actions.deleteSelection(
          selectionState,
          {category: events.categories.rightPanel, action: events.actions.buttonClick},
        ),
        icon: "delete.svg",
        isHidden: !capabilities.write,
        disabled: !NodeUtils.isPlainNode(nodeToDisplay) || isEmpty(selectionState),
      },
      {
        name: "paste",
        onClick: (event) => actions.pasteSelection(
          () => selectionActions.paste(event),
          {category: events.categories.rightPanel, action: events.actions.buttonClick},
        ),
        icon: "paste.svg",
        isHidden: !capabilities.write,
        disabled: !selectionActions.canPaste,
      },
    ],
  }
}
