// from https://github.com/thlorenz/brace/blob/master/mode/sql.js
ace.define("ace/mode/sql_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules","ace/mode/spel_highlight_rules"], function(acequire, exports, module) {
  "use strict";

  var oop = acequire("../lib/oop");
  var TextHighlightRules = acequire("./text_highlight_rules").TextHighlightRules;
  var SpelHighlightRules = acequire("./spel_highlight_rules").CssHighlightRules;

  var SqlHighlightRules = function() {

    var keywords = (
      "select|insert|update|delete|from|where|and|or|group|by|order|limit|offset|having|as|case|" +
      "when|else|end|type|left|right|join|on|outer|desc|asc|union|create|table|primary|key|if|" +
      "foreign|not|references|default|null|inner|cross|natural|database|drop|grant"
    );

    var builtinConstants = (
      "true|false"
    );

    var builtinFunctions = (
      "avg|count|first|last|max|min|sum|ucase|lcase|mid|len|round|rank|now|format|" +
      "coalesce|ifnull|isnull|nvl"
    );

    var dataTypes = (
      "int|numeric|decimal|date|varchar|char|bigint|float|double|bit|binary|text|set|timestamp|" +
      "money|real|number|integer"
    );

    var keywordMapper = this.createKeywordMapper({
      "support.function": builtinFunctions,
      "keyword": keywords,
      "constant.language": builtinConstants,
      "storage.type": dataTypes
    }, "identifier", true);

    this.$rules = {
      "alias": [
        {
          token : ["text","keyword","text"],
          regex : /(\s)(AS)(\s+)/,
          caseInsensitive: true,
          push: [
            {include: "spel"},
            {
              token : "text",
              regex : /\s/,
              next : "pop"
            },
            {defaultToken : "alias"},
          ]
        },
        {
          token : ["text","root","text"],
          regex : /(\s)(\w+)(\.\w+)/,
        },
      ],
      "string": [
        {
          token : "string.start",
          regex : /"/,
          push: [
            {include: "spel"},
            {
              token : "string.end",
              regex : /"/,
              next : "pop"
            },
            {defaultToken : "string"},
          ]
        },
        {
          token : "string.start",
          regex : /'/,
          push: [
            {include: "spel"},
            {
              token : "string.end",
              regex : /'/,
              next : "pop"
            },
            {defaultToken : "string"},
          ]
        },
        {
          token : "string.start",
          regex : /`/,
          push: [
            {include: "spel"},
            {
              token : "string.end",
              regex : /`/,
              next : "pop"
            },
            {defaultToken : "string"},
          ]
        },
      ],
      "spel": [ {
        token: "spel.open",
        regex: /#\{/,
        push: [ {include: "spel-start"} ]
      } ],
      "start" : [ {
        include: "spel"
      }, {
        include: "alias"
      }, {
        token : "comment",
        regex : "--.*$"
      },  {
        token : "comment",
        start : "/\\*",
        end : "\\*/"
      }, {
        include: "string"
      }, {
        token : "constant.numeric", // float
        regex : "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
      }, {
        token : keywordMapper,
        regex : "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
      }, {
        token : "keyword.operator",
        regex : "\\+|\\-|\\/|\\/\\/|%|<@>|@>|<@|&|\\^|~|<|>|<=|=>|==|!=|<>|="
      }, {
        token : "paren.lparen",
        regex : "[\\(]"
      }, {
        token : "paren.rparen",
        regex : "[\\)]"
      }, {
        token : "text",
        regex : "\\s+"
      } ]
    };

    this.embedRules(SpelHighlightRules, "spel-", [{
      token: "spel.close",
      regex: /\}/,
      next: "pop",
    }])

    this.normalizeRules();
  };

  oop.inherits(SqlHighlightRules, TextHighlightRules);

  exports.SqlHighlightRules = SqlHighlightRules;
});

ace.define("ace/mode/sql",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/sql_highlight_rules"], function(acequire, exports, module) {
  "use strict";

  var oop = acequire("../lib/oop");
  var TextMode = acequire("./text").Mode;
  var SqlHighlightRules = acequire("./sql_highlight_rules").SqlHighlightRules;

  var Mode = function() {
    this.HighlightRules = SqlHighlightRules;
    this.$behaviour = this.$defaultBehaviour;
  };
  oop.inherits(Mode, TextMode);

  (function() {

    this.lineCommentStart = "--";

    this.$id = "ace/mode/sql";
  }).call(Mode.prototype);

  exports.Mode = Mode;

});
