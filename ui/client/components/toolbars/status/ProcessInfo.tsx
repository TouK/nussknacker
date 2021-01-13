import React, {memo} from "react"
import {UnknownRecord} from "../../../types/common"
import {CssFade} from "../../CssFade"
import {ProcessStateType, ProcessType} from "../../Process/types"
import {descriptionProcessArchived, descriptionSubprocess, descriptionSubprocessArchived, unknownDescription} from "../../Process/messages"
import {SwitchTransition} from "react-transition-group"
import ProcessStateIcon, {unknownIcon} from "../../Process/ProcessStateIcon"
import {absoluteBePath} from "../../../common/UrlUtils"
import {RootState} from "../../../reducers/index"
import {getFetchedProcessDetails, isStateLoaded, getProcessState} from "../../../reducers/selectors/graph"
import {connect} from "react-redux"
import {DragHandle} from "../../toolbarComponents/DragHandle"
import Deploy from "./buttons/DeployButton"
import Cancel from "./buttons/CancelDeployButton"
import Metrics from "./buttons/MetricsButton"
import {getCapabilities} from "../../../reducers/selectors/other"
import SaveButton from "../process/buttons/SaveButton"
import {ToolbarButtons} from "../../toolbarComponents/ToolbarButtons"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import i18next from "i18next"
import CustomActionButton from "./buttons/CustomActionButton";
import {getProcessDefinitionData} from "../../../reducers/selectors/settings";

type State = UnknownRecord

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

  private getDescription = (
    process: ProcessType,
    processState: ProcessStateType,
    isStateLoaded: boolean,
  ): string => {
    return process.isArchived ?
      process.isSubprocess ?
        descriptionSubprocessArchived() :
        descriptionProcessArchived() :
      process.isSubprocess ?
        descriptionSubprocess() :
        isStateLoaded ?
          processState?.description :
          process?.state?.description || unknownDescription()
  }

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
  ): string => process.isArchived || process.isSubprocess ?
    `${process.id}` :
    `${process.id}-${processState?.icon || process?.state?.icon || unknownIcon}`

  private buttons = [
    <SaveButton key={0}/>,
    <Deploy key={1}/>,
    <Cancel key={2}/>,
    <Metrics key={3}/>
  ]

  render() {
    const {process, processState, isStateLoaded, iconHeight, iconWidth, processDefinitionData} = this.props
    const description = this.getDescription(process, processState, isStateLoaded)
    const icon = this.getIcon(process, processState, isStateLoaded, iconHeight, iconWidth, description)
    const transitionKey = this.getTransitionKey(process, processState)
    const customActions = processDefinitionData.customActions || []
    // TODO: better styling of process info toolbar in case of many custom actions
    const customButtons = customActions.map((a, ix) => <CustomActionButton
        action={a} processId={process.id} processStatus={process.state.status} key={ix + this.buttons.length}
    />)

    return (
      <CollapsibleToolbar title={i18next.t("panels.status.title", "Status")} id="PROCESS-INFO">
        <DragHandle>
          <SwitchTransition>
            <CssFade key={transitionKey}>
              <div className={"panel-process-info"}>
                <div className={"process-info-icon"}>
                  {icon}
                </div>
                <div className={"process-info-text"}>
                  <div className={"process-name"}>{process.name}</div>
                  <div className={"process-info-description"}>{description}</div>
                </div>
              </div>
            </CssFade>
          </SwitchTransition>
          <ToolbarButtons>
            {[...this.buttons, ...customButtons]}
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
  processDefinitionData: getProcessDefinitionData(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(ProcessInfo))

