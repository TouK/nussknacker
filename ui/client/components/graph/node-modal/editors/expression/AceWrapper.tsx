/* eslint-disable i18next/no-literal-string */
import React from "react"
import {IAceEditorProps} from "react-ace/lib/ace"
import {IAceOptions, IEditorProps} from "react-ace/src/types"
import AceEditor from "./ace"

export interface AceWrapperProps extends Pick<IAceEditorProps,
  | "value"
  | "onChange"
  | "onFocus"
  | "onBlur"
  | "commands"
  | "wrapEnabled"> {
  inputProps: {
    language: string,
    readOnly?: boolean,
    rows?: number,
  },
  customAceEditorCompleter,
  showLineNumbers?: boolean,
}

const DEFAULT_OPTIONS: IAceOptions = {
  indentedSoftWrap: false, //removes weird spaces for multiline strings when wrapEnabled=true
  enableLiveAutocompletion: true,
  enableSnippets: false,
  fontSize: 16,
  fontFamily: "'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace", //monospace font seems to be mandatory to make ace cursor work well
  highlightGutterLine: false,
  highlightActiveLine: false,
}

const DEFAULF_EDITOR_PROPS: IEditorProps = {
  $blockScrolling: true,
}

const DEFAULT_COMMANDS = [
  {
    name: "find",
    bindKey: {win: "Ctrl-F", mac: "Command-F"},
    exec: () => false,
  },
]

export default function AceWrapper({
  inputProps,
  customAceEditorCompleter,
  showLineNumbers,
  wrapEnabled = true,
  commands = [],
  ...props
}: AceWrapperProps): JSX.Element {
  const {language, readOnly, rows = 1} = inputProps
  return (
    <AceEditor
      {...props}
      mode={language}
      width={"100%"}
      minLines={rows}
      maxLines={512}
      theme={"nussknacker"}
      showPrintMargin={false}
      cursorStart={-1} //line start
      readOnly={readOnly}
      className={readOnly ? " read-only" : ""}
      wrapEnabled={!!wrapEnabled}
      showGutter={!!showLineNumbers}
      highlightActiveLine={false}
      editorProps={DEFAULF_EDITOR_PROPS}
      setOptions={{...DEFAULT_OPTIONS, showLineNumbers}}
      enableBasicAutocompletion={[customAceEditorCompleter]}
      commands={[...DEFAULT_COMMANDS, ...commands]}
    />
  )
}
