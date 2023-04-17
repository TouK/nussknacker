import React, {useMemo} from "react"
import RawEditor, {RawEditorProps} from "./RawEditor"
import {ExpressionLang} from "./types";

const SpelTemplateEditor = (props: RawEditorProps) => {

  const {expressionObj, ...passProps} = props

  const value = useMemo(() => ({
    expression: expressionObj.expression,
    // Language is used for purpose of syntax highlighting. We don't have separate color theme for spel template -
    // only for spel. Because of that we have to override that.
    language: ExpressionLang.SpEL,
  }), [expressionObj])

  return (
    <RawEditor
      {...passProps}
      expressionObj={value}
      rows={6}
    />
  )
}

export default SpelTemplateEditor
