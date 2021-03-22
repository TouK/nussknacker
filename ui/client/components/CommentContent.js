import PropTypes from "prop-types"
import React from "react"
import {isEmpty} from "lodash"

export default class CommentContent extends React.Component {
  static propTypes = {
    content: PropTypes.string.isRequired,
    commentSettings: PropTypes.object.isRequired,
  }

  newContent = () => {
    if (isEmpty(this.props.commentSettings)) {
      return this.props.content
    } else {
      // eslint-disable-next-line i18next/no-literal-string
      const regex = new RegExp(this.props.commentSettings.matchExpression, "g")
      const replacement = `<a href=${this.props.commentSettings.link} target="_blank">$1</a>`
      return this.props.content.replace(regex, replacement)
    }
  }

  render() {
    //TODO: replace dangerouslySetInnerHTML with something safer
    return(
      <div className={"panel-comment"}>
        <p dangerouslySetInnerHTML={{__html: this.newContent()}}/>
      </div>
    )
  }
}
