import React from "react"
import {UnknownFunction} from "../../../../../types/common"

import AceEditor from "./ace"

type Props = {
  expressionObj: $TodoType,
  onValueChange: UnknownFunction,
  className: string,
}

export default class JsonEditor extends React.Component<Props, {value: string}> {

  static switchableTo = (expressionObj) => true
  static switchableToHint = () => "TODO"
  static notSwitchableToHint = () => "TODO"

  constructor(props) {
    super(props)

    this.state = {
      value: props.expressionObj.expression.replace(/^["'](.*)["']$/, ""),
    }
  }

  onChange = (newValue) => {
    this.setState({
      value: newValue,
    })

    this.props.onValueChange(newValue)
  }

  render() {
    const THEME = "nussknacker"

    //monospace font seems to be mandatory to make ace cursor work well,
    const FONT_FAMILY = "'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace"

    return (
      <React.Fragment>
        <AceEditor
          mode={"json"}
          width={"70%"}
          minLines={5}
          maxLines={50}
          theme={THEME}
          onChange={this.onChange}
          value={this.state.value}
          showPrintMargin={false}
          cursorStart={-1} //line start
          showGutter={true}
          highlightActiveLine={true}
          wrapEnabled={true}
          setOptions={{
            indentedSoftWrap: false, //removes weird spaces for multiline strings when wrapEnabled=true
            enableLiveAutocompletion: false,
            enableSnippets: false,
            showLineNumbers: true,
            fontSize: 16,
            fontFamily: FONT_FAMILY,
            enableBasicAutocompletion: false,
            tabSize: 2,
          }}
        />
      </React.Fragment>
    )
  }
}

