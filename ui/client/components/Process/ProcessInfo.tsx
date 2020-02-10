import React from "react"
import {ProcessStateType, ProcessType} from "./types"
import {
  descriptionProcessArchived,
  descriptionSubprocess,
  descriptionSubprocessArchived,
  unknownDescription,
} from "./messages"
import {CSSTransition, SwitchTransition} from "react-transition-group"
import ProcessStateIcon, {unknownIcon} from "./ProcessStateIcon"
import {absoluteBePath} from "../../common/UrlUtils"

type State = {}

type OwnProps = {
  processState?: ProcessStateType,
  isStateLoaded: boolean,
  process: ProcessType,
  iconHeight: number,
  iconWidth: number,
}

//TODO: In future information about archived process should be return from BE as state.
class ProcessInfo extends React.Component<OwnProps, State> {
  static defaultProps = {
    isStateLoaded: false,
    processState: null,
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

  private getDescription = (process: ProcessType, processState: ProcessStateType, isStateLoaded: boolean): string =>
    process.isArchived ? (process.isSubprocess ? descriptionSubprocessArchived() : descriptionProcessArchived()) : (
      process.isSubprocess ? descriptionSubprocess() : (
        isStateLoaded ? processState?.description : (
          process?.state?.description || unknownDescription()
        )
      )
    )

  private getIcon = (process: ProcessType, processState: ProcessStateType, isStateLoaded: boolean, iconHeight: number, iconWidth:  number, description: string) => {
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

  private getTransitionKey = (process: ProcessType, processState: ProcessStateType): string =>
    process.isArchived || process.isSubprocess ? `${process.id}` :
      `${process.id}-${processState?.icon || process?.state?.icon || unknownIcon}`

  render() {
    const {process, processState, isStateLoaded, iconHeight, iconWidth} = this.props
    const description = this.getDescription(process, processState, isStateLoaded)
    const icon = this.getIcon(process, processState, isStateLoaded, iconHeight, iconWidth, description)
    const transitionKey = this.getTransitionKey(process, processState)

    return (
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
    )
  }
}

export default ProcessInfo
