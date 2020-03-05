import React, {memo} from "react"
import {ProcessStateType, ProcessType} from "./types"
import {descriptionProcessArchived, descriptionSubprocess, descriptionSubprocessArchived, unknownDescription} from "./messages"
import {CSSTransition, SwitchTransition} from "react-transition-group"
import ProcessStateIcon, {unknownIcon} from "./ProcessStateIcon"
import {absoluteBePath} from "../../common/UrlUtils"
import {RootState} from "../../reducers"
import {getFetchedProcessDetails, isStateLoaded, getProcessState} from "../right-panel/selectors/graph"
import {connect} from "react-redux"
import {DragHandle} from "../right-panel/toolbars/DragHandle"
import Deploy from "../right-panel/panels/deploy/buttons/DeployButton"
import Cancel from "../right-panel/panels/deploy/buttons/CancelDeployButton"
import Metrics from "../right-panel/panels/deploy/buttons/MetricsButton"
import {getCapabilities} from "../right-panel/selectors/other"
import SaveButton from "../right-panel/panels/process/buttons/SaveButton"
import {ToolbarButtons} from "./ToolbarButtons"
import {CollapsibleToolbar} from "../right-panel/toolbars/CollapsibleToolbar"

type State = {}

type OwnProps = {
  iconHeight: number,
  iconWidth: number,
}

//TODO: In future information about archived process should be return from BE as state.
class ProcessInfo extends React.Component<OwnProps & StateProps, State> {
  static defaultProps = {
    isStateLoaded: false,
    iconHeight: 32,
    iconWidth: 32,
  }

  static subprocessIcon = "/assets/process/subprocess.svg"
  static archivedIcon = "/assets/process/archived.svg"

  private animationTimeout = {
    enter: 500,
    appear: 500,
    exit: 500,
  }

  private animationListener = (node, done) => node.addEventListener("transitionend", done, false)

  private getDescription = (
    process: ProcessType,
    processState: ProcessStateType,
    isStateLoaded: boolean,
  ): string => process.isArchived ? process.isSubprocess ? descriptionSubprocessArchived() : descriptionProcessArchived() :
    process.isSubprocess ? descriptionSubprocess() :
      isStateLoaded ? processState?.description :
        process?.state?.description || unknownDescription()

  private getIcon = (
    process: ProcessType,
    processState: ProcessStateType,
    isStateLoaded: boolean,
    iconHeight: number,
    iconWidth: number,
    description: string,
  ) => {
    if (process.isArchived || process.isSubprocess) {
      const icon = absoluteBePath(process.isArchived ? ProcessInfo.archivedIcon : ProcessInfo.subprocessIcon)
      return (
        <img alt={description} title={description} src={icon}/>
      )
    }

    return (
      <ProcessStateIcon
        popover={false}
        animation={false}
        process={process}
        processState={processState}
        isStateLoaded={isStateLoaded}
        height={iconHeight}
        width={iconWidth}
      />
    )
  }

  private getTransitionKey = (
    process: ProcessType,
    processState: ProcessStateType,
  ): string => process.isArchived || process.isSubprocess ? `${process.id}` :
    `${process.id}-${processState?.icon || process?.state?.icon || unknownIcon}`

  render() {
    const {process, processState, isStateLoaded, iconHeight, iconWidth, capabilities} = this.props
    const description = this.getDescription(process, processState, isStateLoaded)
    const icon = this.getIcon(process, processState, isStateLoaded, iconHeight, iconWidth, description)
    const transitionKey = this.getTransitionKey(process, processState)

    return (
      <CollapsibleToolbar>
        <DragHandle>
          <SwitchTransition>
            <CSSTransition key={transitionKey} classNames="fade" timeout={this.animationTimeout} addEndListener={this.animationListener}>
              <div className={"panel-process-info"}>
                <div className={"process-info-icon"}>
                  {icon}
                </div>
                <div className={"process-info-text"}>
                  <div className={"process-name"}>{process.name}</div>
                  <div className={"process-info-description"}>{description}</div>
                </div>
              </div>
            </CSSTransition>
          </SwitchTransition>
          <ToolbarButtons>
            {capabilities.write ? <SaveButton/> : null}
            <Metrics/>
            {capabilities.deploy ? <Deploy/> : null}
            {capabilities.deploy ? <Cancel/> : null}
          </ToolbarButtons>
        </DragHandle>
      </CollapsibleToolbar>
    )
  }
}

const mapState = (state: RootState) => ({
  isStateLoaded: isStateLoaded(state),
  process: getFetchedProcessDetails(state),
  capabilities: getCapabilities(state),
  processState: getProcessState(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(ProcessInfo))

