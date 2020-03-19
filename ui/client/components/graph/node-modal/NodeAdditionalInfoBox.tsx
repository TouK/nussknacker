import React, {useState, useEffect} from "react"
import HttpService from "../../../http/HttpService"
import ReactMarkdown from "react-markdown/with-html"
import "../../../stylesheets/markdown.styl"

type Props = {
  processId: string,
  node: any,
}

//Types should match implementations of NodeAdditionalInfo on Backend!
type NodeAdditionalInfo = MarkdownNodeAdditionalInfo

type MarkdownNodeAdditionalInfo = {
    type: "MarkdownNodeAdditionalInfo",
    content: string,
}

export default function NodeAdditionalInfoBox(props: Props) {

  const [additionalData, setAdditionalData] = useState<NodeAdditionalInfo>(null)
  const {
    processId, node,
  } = props

  useEffect(() => {
    if (node?.type) {
      HttpService.getNodeAdditionalData(processId, node).then(res => setAdditionalData(res.data))
    }
  }, [processId, node])

  const type = additionalData?.type
  if (!type) {
    return null
  }
  switch (type){
    case "MarkdownNodeAdditionalInfo":
      // eslint-disable-next-line i18next/no-literal-string
      const linkTarget = "_blank"
      return <ReactMarkdown source={additionalData.content} className="markdownDisplay" linkTarget={linkTarget}/>
    default:
      // eslint-disable-next-line i18next/no-literal-string
      console.warn("Unknown type:", additionalData.type)
      return null
  }
}
