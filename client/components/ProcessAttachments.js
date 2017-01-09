import React from 'react'
import {render} from "react-dom";
import {Scrollbars} from "react-custom-scrollbars";
import {connect} from "react-redux";
import {bindActionCreators} from "redux";
import _ from 'lodash'
import ActionsUtils from "../actions/ActionsUtils";
import DateUtils from '../common/DateUtils'
import ProcessUtils from '../common/ProcessUtils'
import HttpService from "../http/HttpService";
import Dropzone from "react-dropzone";

export class ProcessAttachments_ extends React.Component {

  static propTypes = {
    attachments: React.PropTypes.array.isRequired
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
                <div className="user">
                  <p>{attachment.user}</p>
                </div>
                <p> {attachment.fileName} </p>
                <div className="footer">
                  <div className="details">
                    <p>{ProcessUtils.processDisplayName(attachment.processId, attachment.processVersionId)}</p>
                    <p>{DateUtils.format(attachment.createDate)}</p>
                  </div>
                  <div className="download-attachment">
                    <span onClick={this.downloadAttachment.bind(this, attachment.processId, attachment.processVersionId, attachment.id)}
                          className="glyphicon glyphicon-download-alt"></span>
                  </div>
                </div>
                <hr/>
              </div>
            )
          })}
        </ul>
        <div className="add-attachments">
          <Dropzone onDrop={this.addAttachment} disableClick={this.state.pendingRequest}
                    className={"dropZone espButton " + (this.state.pendingRequest ? "disabled" : "")}  >
            <div>Add</div>
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