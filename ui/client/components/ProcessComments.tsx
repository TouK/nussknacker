import React, {useCallback, useState} from "react"
import {useDispatch, useSelector} from "react-redux"
import {createSelector} from "reselect"
import {addComment, deleteComment, toggleConfirmDialog} from "../actions/nk"
import * as DialogMessages from "../common/DialogMessages"
import {getProcessId, getProcessVersionId} from "../reducers/selectors/graph"
import {getFeatureSettings, getLoggedUser} from "../reducers/selectors/settings"
import CommentContent from "./CommentContent"
import CommentInput from "./CommentInput"
import Date from "./common/Date"
import {ListSeparator} from "./common/ListSeparator"
import {NkButton} from "./NkButton"

const getComments = state => state.processActivity?.comments || []
const getCommentSettings = createSelector(getFeatureSettings, f => f.commentSettings || {})

function ProcessComments(): JSX.Element {
  const [comment, setComment] = useState("")
  const [pending, setPending] = useState(false)
  const dispatch = useDispatch()

  const comments = useSelector(getComments)
  const processId = useSelector(getProcessId)
  const processVersionId = useSelector(getProcessVersionId)
  const loggedUser = useSelector(getLoggedUser)
  const commentSettings = useSelector(getCommentSettings)

  const _addComment = useCallback(async () => {
    setPending(true)
    await dispatch(addComment(processId, processVersionId, comment))
    setPending(false)
    setComment("")
  }, [dispatch, processId, processVersionId, comment])

  const _deleteComment = useCallback((comment) => {
    dispatch(toggleConfirmDialog(true, DialogMessages.deleteComment(), async () => {
      setPending(true)
      await dispatch(deleteComment(processId, comment.id))
      setPending(false)
    }, "DELETE", "NO"))
  }, [dispatch, processId])

  const onInputChange = useCallback((e) => setComment(e.target.value), [])
  const isLastComment = useCallback((index) => index + 1 === comments.length, [comments.length])

  return (
    <div className="process-comments">
      <ul className="process-comments-list">
        {comments.map((comment, index) => (
          <div key={comment.id}>
            <div className="header">
              <Date date={comment.createDate}/>
              <span className="comment-header">{`| v${comment.processVersionId} | ${comment.user}`}</span>
              {comment.user != loggedUser.id ?
                null :
                (
                  <span className="remove glyphicon glyphicon-remove" onClick={() => _deleteComment(comment)}/>
                )}
            </div>
            <CommentContent content={comment.content} commentSettings={commentSettings}/>
            {!isLastComment(index) && (
              <ListSeparator/>
            )}
          </div>
        ))}
      </ul>
      <div className="add-comment-panel">
        <CommentInput onChange={onInputChange.bind(this)} value={comment}/>
        <NkButton
          type="button"
          className="add-comment"
          onClick={_addComment}
          disabled={pending || comment == ""}
        >Add</NkButton>
      </div>
    </div>
  )
}

export default ProcessComments

