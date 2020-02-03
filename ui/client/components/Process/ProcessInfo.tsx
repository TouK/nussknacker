import React from "react"
import {ProcessType} from "./ProcessTypes"
import {descriptionArchived, descriptionProcess, descriptionSubprocess} from "./ProcessMessages"
import {absoluteBePath} from "../../common/UrlUtils"

type State = {}

type OwnProps = {
  process: ProcessType,
  iconHeight: number,
  iconWidth: number,
}

class ProcessInfo extends React.Component<OwnProps, State> {
  static defaultProps = {
    process: null,
    iconHeight: 32,
    iconWidth: 32,
  }

  static subprocessIcon = "/assets/process/subprocess.svg"
  static archivedIcon = "/assets/process/archived.svg"

  getDescription = (process: ProcessType): string =>
    process.isArchived ? descriptionArchived() : descriptionSubprocess()

  getIcon = (process: ProcessType): string =>
    absoluteBePath(process.isArchived ? ProcessInfo.archivedIcon : ProcessInfo.subprocessIcon)

  render() {
    const {process, iconHeight, iconWidth} = this.props
    const description = this.getDescription(process)
    const icon = this.getIcon(process)

    return (
      <div>
        <div className={"panel-process-info"}>
          <div className={"process-info-icon"}>
            <img src={icon} width={iconWidth} height={iconHeight} title={description} alt={description}/>
          </div>
          <div className={"process-info-text"}>
            <div className={"process-name"}>{process.name}</div>
            <div className={"process-info-description"}>{description}</div>
          </div>
        </div>
      </div>
    )
  }
}

export default ProcessInfo
