import React from "react"
import {Scrollbars} from "react-custom-scrollbars"
import {connect} from "react-redux"
import {mapDispatchWithEspActions} from "../actions/ActionsUtils"
import {unsavedProcessChanges} from "../common/DialogMessages"
import ProcessUtils from "../common/ProcessUtils"
import styles from "../stylesheets/processHistory.styl"
import Date from "./common/Date"
import {compose} from "redux"
import {WithTranslation} from "react-i18next/src"
import {withTranslation} from "react-i18next"
import {ProcessVersionType} from "./Process/types"

type OwnProps = {}

type State = {
  currentVersion: ProcessVersionType,
}

export class ProcessHistoryComponent extends React.Component<Props, State> {

  state = {
    currentVersion: null,
  }

  doChangeProcessVersion = (version: ProcessVersionType) => {
    this.setState({currentVersion: version})
    return this.props.actions.fetchProcessToDisplay(this.props.process.name, version.processVersionId, this.props.businessView)
  }

  onChangeProcessVersion = (version: ProcessVersionType) => () => {
    if (this.props.nothingToSave) {
      this.doChangeProcessVersion(version)
    } else {
      this.props.actions.toggleConfirmDialog(true, unsavedProcessChanges(), () => {
        this.doChangeProcessVersion(version)
      }, "DISCARD", "NO", null)
    }
  }

  processVersionOnTimeline = (version: ProcessVersionType, index: number) => {
    const {currentVersion} = this.state

    if (!currentVersion) {
      // eslint-disable-next-line i18next/no-literal-string
      return index === 0 ? "current" : "past"
    }

    // eslint-disable-next-line i18next/no-literal-string
    return version.createDate === currentVersion.createDate ? "current" : version.createDate < currentVersion.createDate ? "past" : "future"
  }

  latestVersionIsNotDeployed = (index, version: ProcessVersionType) => {
    const deployedProcessVersionId = this.props?.lastDeployedAction?.processVersionId
    return index === 0 && (!deployedProcessVersionId || version.processVersionId !== deployedProcessVersionId)
  }

  render() {
    const {t, history, lastDeployedAction} = this.props

    return (
      <Scrollbars renderTrackVertical={(props) => <div {...props} className={styles.innerScroll}/>} renderTrackHorizontal={() => <div className="hide"/>} autoHeight autoHeightMax={300} hideTracksWhenNotNeeded={true}>
        <ul id="process-history">
          {history.map((version: ProcessVersionType, index: number) => {
            return (
              <li key={index} className={this.processVersionOnTimeline(version, index)} onClick={this.onChangeProcessVersion(version)}>
                {`v${version.processVersionId}`} | {version.user}
                {this.latestVersionIsNotDeployed(index, version) ?
                  <small><span title={t("processHistory.lastVersionIsNotDeployed", "Last version is not deployed")} className="glyphicon glyphicon-warning-sign"/></small> :
                  null
                }
                <br/>
                <small><i><Date date={version.createDate}/></i></small>
                <br/>
                {version.processVersionId === lastDeployedAction?.processVersionId ? (
                  <small key={index}>
                    <i><Date date={lastDeployedAction?.performedAt}/></i>
                    <span className="label label-info">
                      {t("processHistory.lastDeployed", "Last deployed")}
                    </span>
                  </small>
                ) : null
                }
              </li>
            )
          })}
        </ul>
      </Scrollbars>
    )
  }
}

function mapState(state) {
  return {
    process: state?.graphReducer?.fetchedProcessDetails,
    history: state?.graphReducer?.fetchedProcessDetails?.history || [],
    lastDeployedAction: state?.graphReducer?.fetchedProcessDetails?.lastDeployedAction || null,
    nothingToSave: ProcessUtils.nothingToSave(state),
    businessView: state?.graphReducer?.businessView || false,
  }
}

type Props = OwnProps & ReturnType<typeof mapDispatchWithEspActions> & ReturnType<typeof mapState> & WithTranslation

const enhance = compose(
  connect(mapState, mapDispatchWithEspActions),
  withTranslation(),
)

export default enhance(ProcessHistoryComponent)
