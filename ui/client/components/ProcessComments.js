import React from 'react'
import {connect} from "react-redux";
import _ from 'lodash'
import ActionsUtils from "../actions/ActionsUtils";
import DateUtils from '../common/DateUtils'
import DialogMessages from '../common/DialogMessages'
import CommentContent from "./CommentContent";
import CommentInput from "./CommentInput";

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
    this.props.actions.addComment(this.props.processId, this.props.processVersionId, this.state.comment).then((response) => {
      this.setState(this.initState)
    })
  }

  deleteComment = (comment) => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.deleteComment(), () => {
      this.setState({ pendingRequest: true})
      this.props.actions.deleteComment(this.props.processId, comment.id).then((response) => {
        this.setState(this.initState)
      })
    })
  }

  onInputChange = (e) => {
    this.setState({comment: e.target.value})
  }

  lastComment = (idx) => {
    return idx + 1 === this.props.comments.length;
  }

  render() {
    return (
      <div className="process-comments">
        <ul className="process-comments-list">
          {_.map(this.props.comments, (comment, idx) => {
            return (
              <div key={idx}>
                <div className="header">
                  <span className="date">{DateUtils.format(comment.createDate)}</span>
                  <span className="comment-header">v{comment.processVersionId} ({comment.user})</span>
                  {comment.user == this.props.loggedUser.id ?
                    <span className="remove glyphicon glyphicon-remove" onClick={this.deleteComment.bind(this, comment)}/>
                    : null}
                </div>
                <CommentContent content={comment.content} commentSettings={this.props.commentSettings}/>
                {this.lastComment(idx) ? null : <hr className='comment-under-line'/>}
              </div>
            )
          })}
        </ul>
        <div className="add-comment">
          <CommentInput onChange={this.onInputChange.bind(this)} value={this.state.comment} />
          <button
            type="button"
            className="espButton add-comment"
            onClick={this.addComment}
            disabled={this.state.pendingRequest || this.state.comment == "" }
          >
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
    processVersionId: _.get(state.graphReducer, 'fetchedProcessDetails.processVersionId'),
    loggedUser: state.settings.loggedUser || {},
    commentSettings: _.get(state.settings, "featuresSettings.commentSettings") || {}
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessComments);

