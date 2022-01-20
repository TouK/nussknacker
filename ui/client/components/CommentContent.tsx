import React, {useMemo} from "react"
import {isEmpty} from "lodash"
import xss from "xss"

interface Props {
  content: string,
  commentSettings: { matchExpression?: string, link?: string },
}

function CommentContent({commentSettings, content}: Props): JSX.Element {
  const newContent = useMemo(() => {
    if (isEmpty(commentSettings)) {
      return content
    } else {
      // eslint-disable-next-line i18next/no-literal-string
      const regex = new RegExp(commentSettings.matchExpression, "g")
      const replacement = `<a href=${commentSettings.link} target="_blank">$1</a>`
      return content.replace(regex, replacement)
    }
  }, [commentSettings, content])

  const __html = useMemo(() => xss(newContent, {
    whiteList: {
      // eslint-disable-next-line i18next/no-literal-string
      a: ["href", "title", "target"],
    },
  }), [newContent])

  return (
    <div className={"panel-comment"}>
      <p dangerouslySetInnerHTML={{__html}}/>
    </div>
  )
}

export default CommentContent
