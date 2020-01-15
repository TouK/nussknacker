import PropTypes from "prop-types"
import React from "react"

const CommentInput = (props) => {
  return (
    <textarea
      value={props.value || ""}
      placeholder="Write a comment..."
      onChange={props.onChange}
    />
  )
}

CommentInput.propTypes = {
  onChange: PropTypes.func,
}

export default CommentInput
