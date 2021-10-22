import i18next from "i18next"
import React, {memo} from "react"
import {connect} from "react-redux"
import {SwitchTransition} from "react-transition-group"
import {RootState} from "../../../reducers"
import {
  getFetchedProcessDetails,
  getProcessState,
  getProcessUnsavedNewName,
  isProcessRenamed,
  isProcessStateLoaded,
} from "../../../reducers/selectors/graph"
import {getCustomActions} from "../../../reducers/selectors/settings"
import {UnknownRecord} from "../../../types/common"
import {CssFade} from "../../CssFade"
import ProcessStateIcon from "../../Process/ProcessStateIcon"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import {DefaultToolbarPanel, ToolbarPanelProps} from "../../toolbarComponents/DefaultToolbarPanel"
import {DragHandle} from "../../toolbarComponents/DragHandle"
import {ToolbarButtons} from "../../toolbarComponents/ToolbarButtons"
import {ActionButton} from "../../toolbarSettings/buttons"
import ProcessStateUtils from "../../Process/ProcessStateUtils"

type State = UnknownRecord

class ProcessInfo extends React.Component<ToolbarPanelProps & StateProps, State> {
  static defaultProps = {
    isStateLoaded: false,
  }

  render() {
    const {process, processState, isStateLoaded, customActions, isRenamePending, unsavedNewName} = this.props
    const description = ProcessStateUtils.getStateDescription(process, processState, isStateLoaded)
    const transitionKey = ProcessStateUtils.getTransitionKey(process, processState)
    // TODO: better styling of process info toolbar in case of many custom actions
    return (
      <CollapsibleToolbar title={i18next.t("panels.status.title", "Status")} id={this.props.id}>
        <DragHandle>
          <SwitchTransition>
            <CssFade key={transitionKey}>
              <div className={"panel-process-info"}>
                <div className={"process-info-icon"}>
                  <ProcessStateIcon
                    popover={false}
                    animation={false}
                    process={process}
                    processState={processState}
                    isStateLoaded={isStateLoaded}
                    height={32}
                    width={32}
                  />
                </div>
                <div className={"process-info-text"}>
                  {isRenamePending ?
                    (
                      <div className="process-name process-name-rename" title={process.name}>{unsavedNewName}*</div>
                    ) :
                    (
                      <div className="process-name">{process.name}</div>
                    )}
                  <div className={"process-info-description"}>{description}</div>
                </div>
              </div>
            </CssFade>
          </SwitchTransition>
          <ToolbarButtons variant={this.props.buttonsVariant}>
            {this.props.children}
            {
              //TODO: to be replaced by toolbar config
              customActions.map(action => (<ActionButton name={action.name} key={action.name}/>))
            }
          </ToolbarButtons>
        </DragHandle>
      </CollapsibleToolbar>
    )
  }
}

const mapState = (state: RootState) => ({
  isStateLoaded: isProcessStateLoaded(state),
  process: getFetchedProcessDetails(state),
  isRenamePending: isProcessRenamed(state),
  unsavedNewName: getProcessUnsavedNewName(state),
  processState: getProcessState(state),
  customActions: getCustomActions(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(ProcessInfo)) as typeof DefaultToolbarPanel

