import "ace-builds/src-noconflict/ace";
import "ace-builds/src-noconflict/ext-language_tools";
import "ace-builds/src-noconflict/ext-searchbox";
import "ace-builds/src-noconflict/mode-markdown";
import "ace-builds/src-noconflict/mode-json";
import "ace-builds/src-noconflict/snippets/json";
import "ace-builds/src-noconflict/mode-jsx";
import ace from "ace-builds";
import _AceEditor from "react-ace";

import "../../../../../brace/mode/spel";
import "../../../../../brace/mode/spelTemplate";
import "../../../../../brace/mode/jsonTemplate";
import "../../../../../brace/mode/sql";
import "../../../../../brace/mode/plainText";
import "../../../../../brace/theme/nussknacker";

export const AceEditor = _AceEditor;
export type AceEditorType = _AceEditor;

/**
 * There is a problem with Ace editor tooltip positioning in the modal, because of modal transform property.
 * To avoid it, we need to set the parent node of the tooltip to document.body, to keep correct positioning for fixed position element
 */
const Tooltip = ace.require("ace/tooltip").Tooltip;
const originalInit = Tooltip.prototype.$init;
Tooltip.prototype.$init = function () {
    this.$parentNode = document.body;
    return originalInit.call(this);
};
