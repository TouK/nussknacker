import _ from "lodash"
import React from "react"
import Dropzone from "react-dropzone"
import {withTranslation} from "react-i18next"
import {WithTranslation} from "react-i18next/src"
import {connect} from "react-redux"
import {mapDispatchWithEspActions} from "../actions/ActionsUtils"
import InlinedSvgs from "../assets/icons/InlinedSvgs"
import {UnknownRecord} from "../types/common"
import {NkButton} from "./NkButton"
import HttpService from "../http/HttpService"
import {RootState} from "../reducers/index"
import Date from "./common/Date"
import {FocusOutline, InputWithFocus} from "./withFocus"

type State = { pendingRequest: boolean }

type OwnProps = UnknownRecord & WithTranslation

export class ProcessAttachments extends React.Component<Props, State> {
  private initState: State

  constructor(props) {
    super(props)
    this.initState = {pendingRequest: false}
    this.state = this.initState
  }

  addAttachment = (files: File[]) => {
    this.setState({pendingRequest: true})
    Promise.all(files.map((file)=> this.props.actions.addAttachment(this.props.processId, this.props.processVersionId, file))).then(() => {
      this.setState(this.initState)
    })
  }

  render() {
    const {attachments, t} = this.props
    return (
      <div className="process-attachments">
        <ul className="process-attachments-list">
          {attachments.map((attachment, idx) => (
            <div key={idx} className={"attachment-section"}>
              <div className="download-attachment">
                <NkButton
                  className="download-button"
                  dangerouslySetInnerHTML={{__html: InlinedSvgs.buttonDownload}}
                  onClick={() => HttpService.downloadAttachment(attachment.processId, attachment.processVersionId, attachment.id)}
                />
              </div>
              <div className={"attachment-details"}>
                <div className="header">
                  <Date date={attachment.createDate}/>
                  <span>{` | v${attachment.processVersionId} | ${attachment.user}`}</span>
                </div>
                <p> {attachment.fileName} </p>
              </div>
            </div>
          ))}
        </ul>
        <FocusOutline className="add-attachments">
          <Dropzone onDrop={this.addAttachment}>
            {({getRootProps, getInputProps}) => (
              <FocusOutline className="attachments-container" {...getRootProps()}>
                <FocusOutline
                  className={"attachment-drop-zone attachment-button"}
                  dangerouslySetInnerHTML={{__html: InlinedSvgs.buttonUpload_1}}
                />
                <div className="attachment-button-text">
                  <span>{t("attachments.buttonText", "drop or choose a file")}</span>
                </div>
                <InputWithFocus {...getInputProps()}/>
              </FocusOutline>
            )}
          </Dropzone>
        </FocusOutline>
      </div>
    )
  }
}

function mapState(state: RootState) {
  return {
    attachments: state.processActivity.attachments,
    processId: _.get(state.graphReducer, "fetchedProcessDetails.id"),
    processVersionId: _.get(state.graphReducer, "fetchedProcessDetails.processVersionId"),
  }
}

type Props = OwnProps & ReturnType<typeof mapDispatchWithEspActions> & ReturnType<typeof mapState>

export default connect(mapState, mapDispatchWithEspActions)(withTranslation()(ProcessAttachments))
