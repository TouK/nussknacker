import React from 'react'
import {render} from "react-dom";
import {Scrollbars} from "react-custom-scrollbars";
import {connect} from "react-redux";
import {bindActionCreators} from "redux";
import _ from 'lodash'
import ActionsUtils from "../actions/ActionsUtils";
import DateUtils from '../common/DateUtils'
import ProcessUtils from '../common/ProcessUtils'

class ProcessComments extends React.Component {

  constructor(props) {
    super(props);
    this.initState = {
      comment: "",
      pendingRequest: false
    }
    this.state = this.initState
  }

  addComment = () => {
    this.setState({ pendingRequest: true})
    this.props.actions.addComment(this.props.processId, this.props.processVersionId, this.state.comment).then((resp) => {
      this.setState(this.initState)
    })
  }

  render() {
    return (
      <div className="process-comments">
        <ul className="process-comments-list">
          {_.map(this.props.comments, (comment, idx) => {
            return (
              <div key={idx}>
                <div className="header">
                  <span className="label label-info">{comment.user}</span>
                  <span className="date">{DateUtils.format(comment.createDate)}</span>
                  <p>{ProcessUtils.processDisplayName(comment.processId, comment.processVersionId)}</p>
                </div>
                <p>{comment.content}</p>
              </div>
            )
          })}
        </ul>
        <div className="add-comment">
          <textarea placeholder="Write a comment..." value={this.state.comment} onChange={(e) => { this.setState({comment: e.target.value}) } } />
          <button type="button" className="espButton add-comment" onClick={this.addComment}
                  disabled={this.state.pendingRequest || _.isEmpty(this.state.comment) }>
            Add
          </button>
        </div>
      </div>
    )
  }

}

function mapState(state) {
  return {
    comments: _.get(state.processActivity, 'comments', []),
    processId: _.get(state.graphReducer, 'fetchedProcessDetails.id'),
    processVersionId: _.get(state.graphReducer, 'fetchedProcessDetails.processVersionId')
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessComments);

