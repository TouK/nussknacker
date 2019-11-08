import React from 'react'
import {connect} from "react-redux";
import PropTypes from 'prop-types';
import _ from 'lodash'
import ActionsUtils from "../actions/ActionsUtils";
import DateUtils from '../common/DateUtils'
import ProcessUtils from '../common/ProcessUtils'
import HttpService from "../http/HttpService";
import Dropzone from 'react-dropzone'
import InlinedSvgs from '../assets/icons/InlinedSvgs'

export class ProcessAttachments_ extends React.Component {

  static propTypes = {
    attachments: PropTypes.array.isRequired
  }

  constructor(props) {
    super(props);
    this.initState = { pendingRequest: false }
    this.state = this.initState
  }

  addAttachment = (files) => {
    this.setState({ pendingRequest: true})
    Promise.all(files.map((file)=>
      this.props.actions.addAttachment(this.props.processId, this.props.processVersionId, file)
    )).then ((resp) => {
      this.setState(this.initState)
    })
  }

  downloadAttachment = (processId, processVersionId, attachmentId) => {
    HttpService.downloadAttachment(processId, processVersionId, attachmentId)
  }

  render() {
    return (
      <div className="process-attachments">
        <ul className="process-attachments-list">
          {_.map(this.props.attachments, (attachment, idx) => {
            return (
              <div key={idx}>
                <div className="download-attachment">
                  <div
                      className="espButton download-button"
                      dangerouslySetInnerHTML={{__html: InlinedSvgs.buttonDownload}}
                      onClick={this.downloadAttachment.bind(this, attachment.processId, attachment.processVersionId, attachment.id)}
                  />
                </div>
                <div className="header">
                  <span className="label label-info">{attachment.user}</span>
                  <span title={DateUtils.formatAbsolutely(attachment.createDate)} className="date">{DateUtils.formatRelatively(attachment.createDate)}</span>
                  <p>{ProcessUtils.processDisplayName(attachment.processId, attachment.processVersionId)}</p>
                </div>
                <p> {attachment.fileName} </p>
              </div>
            )
          })}
        </ul>
        <div className="add-attachments">
          <Dropzone onDrop={this.addAttachment}>
            {({getRootProps, getInputProps}) => (
              <div {...getRootProps()}>
                <div className={"attachment-drop-zone attachment-button"} dangerouslySetInnerHTML={{__html: InlinedSvgs.buttonUpload_1}} />
                <div className="attachment-button-text"><span>drop or choose a file</span></div>
                <input {...getInputProps()} />
              </div>
            )}
          </Dropzone>
        </div>
      </div>
    )
  }
}

function mapState(state) {
  return {
    attachments: _.get(state.processActivity, 'attachments', []),
    processId: _.get(state.graphReducer, 'fetchedProcessDetails.id'),
    processVersionId: _.get(state.graphReducer, 'fetchedProcessDetails.processVersionId')
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessAttachments_);