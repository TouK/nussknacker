ace.define("ace/mode/sqlSpelTemplate", ["require", "exports", "module", "ace/lib/oop", "ace/mode/text", "ace/mode/sql_highlight_rules"], function (acequire, exports, module) {
  "use strict";

  var oop = acequire("../lib/oop");
  var TextMode = acequire("./text").Mode;
  var SqlHighlightRules = acequire("./sql_highlight_rules").SqlHighlightRules;

  var Mode = function () {
      this.HighlightRules = SqlHighlightRules;
      this.$behaviour = this.$defaultBehaviour;
  };
  oop.inherits(Mode, TextMode);

  (function () {

      this.lineCommentStart = "--";

      this.$id = "ace/mode/sqlSpelTemplate";
  }).call(Mode.prototype);

  exports.Mode = Mode;

});