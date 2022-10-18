import React from "react"

import AceEditor from "./ace"
import {ExpressionObj} from "./types"
import ValidationLabels from "../../../../modals/ValidationLabels";
import {Validator} from "../Validators";

type Props = {
  expressionObj: ExpressionObj,
  onValueChange: (value: string) => void,
  className: string,
  showValidation: boolean,
  validators: Validator[],
}

export default class JsonEditor extends React.Component<Props, { value: string }> {

  static switchableTo = (expressionObj) => true
  static switchableToHint = () => "TODO"
  static notSwitchableToHint = () => "TODO"

  constructor(props) {
    super(props)

    this.state = {
      value: props.expressionObj.expression.replace(/^["'](.*)["']$/, ""),
    }
  }

  onChange = (newValue: string) => {
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
      <div className="node-value">
        <AceEditor
          mode={"json"}
          width={"100%"}
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
        {this.props.showValidation &&
          <ValidationLabels validators={this.props.validators} values={[this.state.value]}/>}
      </div>
    )
  }
}

