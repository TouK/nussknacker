import "ace-builds/webpack-resolver"
import "ace-builds/src-noconflict/ext-language_tools"
import "ace-builds/src-noconflict/ext-searchbox"
import "ace-builds/src-noconflict/mode-json"
import "ace-builds/src-noconflict/mode-jsx"
import AceEditor from "react-ace"

import "../../../../../brace/mode/spel"
import "../../../../../brace/mode/sql"
import "../../../../../brace/theme/nussknacker"

export default AceEditor
