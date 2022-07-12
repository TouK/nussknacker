/* eslint-disable i18next/no-literal-string */
import React, {ForwardedRef, forwardRef, useMemo} from "react"
import ReactAce, {IAceEditorProps} from "react-ace/lib/ace"
import {IAceOptions, IEditorProps} from "react-ace/src/types"
import AceEditor from "./ace"
import {ICommand} from "react-ace/lib/types"
import type {Ace} from "ace-builds"
import {trimStart} from "lodash"

export interface AceWrapperProps extends Pick<IAceEditorProps,
  | "value"
  | "onChange"
  | "onFocus"
  | "onBlur"
  | "wrapEnabled"> {
  inputProps: {
    language: string,
    readOnly?: boolean,
    rows?: number,
  },
  customAceEditorCompleter?,
  showLineNumbers?: boolean,
  commands?: AceKeyCommand[],
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

export interface AceKeyCommand extends Omit<ICommand, "exec"> {
  readonly?: boolean,
  exec: (editor: Ace.Editor) => boolean | void,
}

function getTabindexedElements(root: Element, currentElement?: HTMLElement) {
  const treeWalker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, (node: HTMLElement) => {
    if (currentElement === node) {
      return NodeFilter.FILTER_ACCEPT
    }
    if (currentElement === node.parentElement) {
      return NodeFilter.FILTER_REJECT
    }
    if (node.tabIndex < Math.max(0, currentElement?.tabIndex)) {
      return NodeFilter.FILTER_SKIP
    }
    return NodeFilter.FILTER_ACCEPT
  })

  const elements: HTMLElement[] = []
  let node
  while (node = treeWalker.nextNode()) {
    elements.push(node as HTMLElement)
  }

  const htmlElements = elements.sort((a, b) => Math.max(0, a.tabIndex) - Math.max(0, b.tabIndex))
  if (currentElement) {
    const index = htmlElements.indexOf(currentElement)
    const nextElements = htmlElements.slice(index + 1)
    const prevElements = htmlElements.slice(0, index)
    return [nextElements, prevElements]
  }
  return [htmlElements, []]
}

function handleTab(editor: Ace.Editor, shiftKey?: boolean): boolean {
  const session = editor.getSession()
  if (session.getDocument().getAllLines().length > 1) {
    const selection = editor.getSelection()

    // allow indent multiple lines
    if (selection.isMultiLine() || selection.getAllRanges().length > 1) {
      return false
    }

    const {row, column} = selection.getCursor()
    const line = session.getLine(row).slice(0, column)
    const trimmed = trimStart(line).length
    // check if cursor is within whitespace starting part of line
    // always allow indent decrease
    if (!trimmed || shiftKey && trimmed < line.length) {
      return false
    }
  }

  editor.blur()

  const [nextElements, prevElements] = getTabindexedElements(editor.container.offsetParent, editor.container)
  const element = shiftKey ? prevElements.pop() : nextElements.shift()
  element?.focus()
}

export default forwardRef(function AceWrapper({
  inputProps,
  customAceEditorCompleter,
  showLineNumbers,
  wrapEnabled = true,
  commands = [],
  ...props
}: AceWrapperProps, ref: ForwardedRef<ReactAce>): JSX.Element {
  const {language, readOnly, rows = 1} = inputProps

  const DEFAULT_COMMANDS = useMemo<AceKeyCommand[]>(() => [
      {
        name: "find",
        bindKey: {win: "Ctrl-F", mac: "Command-F"},
        exec: () => false,
      },
      {
        name: "focusNext",
        bindKey: {win: "Tab", mac: "Tab"},
        exec: (editor) => handleTab(editor),
      },
      {
        name: "focusPrevious",
        bindKey: {win: "Shift-Tab", mac: "Shift-Tab"},
        exec: (editor) => handleTab(editor, true),
      },
    ],
    [])

  return (
    <AceEditor
      {...props}
      ref={ref}
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
      enableBasicAutocompletion={customAceEditorCompleter && [customAceEditorCompleter]}
      commands={[...DEFAULT_COMMANDS, ...commands] as ICommand[]}
    />
  )
})
