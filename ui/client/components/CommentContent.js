import React from "react";

export default class CommentContent extends React.Component {
  static propTypes = {
    content: React.PropTypes.string.isRequired,
    commentSettings: React.PropTypes.object.isRequired
  }

  newContent = () => {
    if (_.isEmpty(this.props.commentSettings)) {
      return this.props.content
    } else {
      const regex = new RegExp(this.props.commentSettings.matchExpression, "g")
      const replacement = `<a href=${this.props.commentSettings.link} target="_blank">$1</a>`
      return this.props.content.replace(regex, replacement)
    }
  }

  render() {
     return(
       <div className={"panel-comment"} dangerouslySetInnerHTML={{__html: this.newContent()}} />
     )
  }
}