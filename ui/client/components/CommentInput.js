import React from "react"

const CommentInput = (props) => {
  return (
    <textarea
      className="add-comment-on-save"
      placeholder="Write a comment..."
      onChange={props.onChange}
    />
  )
}

CommentInput.propTypes = {
  onChange: React.PropTypes.func
}

export default CommentInput
