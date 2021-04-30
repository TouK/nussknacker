function quotedStrings(quotes, next = "pop", token = "string") {
  return quotes.map(q => ({
    token: `${token}.start`,
    regex: `${q}`,
    push: [
      {include: "spel"},
      {
        token: `${token}.escaped`,
        regex: `\\\\${q}`,
      },
      {
        token: `${token}.end`,
        regex: `${q}`,
        next,
      },
      {defaultToken: token},
    ],
  }))
}

const popState = (n = 1) => (state, stack) => {
  let currentState = state
  for (let i = n; i > 0; i--) {
    stack.shift()
    currentState = stack.shift() || "start"
  }
  return currentState
}

// from https://github.com/thlorenz/brace/blob/master/mode/sql.js
ace.define("ace/mode/sql_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules","ace/mode/spel_highlight_rules"], function(acequire, exports, module) {
  "use strict";

  var oop = acequire("../lib/oop");
  var TextHighlightRules = acequire("./text_highlight_rules").TextHighlightRules;
  var SpelHighlightRules = acequire("./spel_highlight_rules").CssHighlightRules;

  var SqlHighlightRules = function() {

    var keywords = [
      "select","insert","update","delete","from","where","and","or","group","by","order","limit","offset","having","as","case",
      "when","else","end","type","left","right","join","on","outer","desc","asc","union","create","table","primary","key","if",
      "foreign","not","references","default","inner","cross","natural","database","drop","grant",
      "is","with","procedure",
      "then","in","all","any",
      "distinct","between",
      "partition",
      "full","apply","only","rows"
    ].join('|');

    var builtinConstants = [
      "true","false","null"
    ].join('|');

    var builtinFunctions = [
      "avg","count","first","last","max","min","sum","ucase","lcase","mid","len","round","rank","now","format",
      "coalesce","ifnull","isnull","nvl","to_char", "to_number","sysdate",
      "rownum","interval","month",
      "fetch"
    ].join('|');

    var dataTypes = [
      "int","numeric","decimal","date","varchar","char","bigint","float","double","bit","binary","text","set","timestamp",
      "money","real","number","integer"
    ].join('|');

    var keywordMapper;

    const keywordRule = {
      token : (value) => keywordMapper(value),
      regex : "(\\.?[a-zA-Z_$][a-zA-Z0-9_$]*\\b)"
    }

    this.setAliases = (aliases = "") => {
      keywordMapper = this.createKeywordMapper({
        "support.function": builtinFunctions,
        "keyword": keywords,
        "constant.language": builtinConstants,
        "storage.type": dataTypes,
        "alias.used": aliases,
      }, "identifier", true)
    }

    this.setAliases()

    const reservedWords = `(${keywords}|${builtinFunctions}|${dataTypes})`
    const fnStart = `\\s?(?!(${reservedWords}\\W))\\w+\\(`
    const builtInFnStart = `(${builtinFunctions})\\s*\\(`
    this.$rules = {
      "alias": [
        {
          // WITH xxx
          token: ["text", "keyword", "text"],
          regex: /(^|\s?)(WITH)(?=(\s+|$))/,
          caseInsensitive: true,
          push: [
            {include: "spel"},
            {include: "alias"},
            {
              token: "alias",
              regex: `\\w+(?=(\\W+(AS)|#{))`,
              caseInsensitive: true,
            },
            ...quotedStrings([`"`, `'`], popState(1)),
            {
              token: "text",
              regex: `(^|\\W)(?=(${fnStart}|${reservedWords}(\\W|$)))`,
              next: "pop",
            }
          ],
        },
        {
          // AS() | AS ()
          token: ["text", "keyword", "alias.paren.start"],
          regex: /(^|\s+?)(AS)(\s*?\()/,
          caseInsensitive: true,
          push: [
            {
              token: "alias.paren.end",
              regex: /\)/,
              next: "pop",
            },
            {include: "start"},
          ],
        },
        {
          // AS xxx
          token: ["text", "keyword", "text"],
          regex: /(^|\s?)(AS)(?=(\s+|$))/,
          caseInsensitive: true,
          push: [
            {
              token: "text",
              regex: `(^|\\W)(?=(${fnStart}|${reservedWords}(\\W|$)))`,
              next: "pop",
            },
            {include: "spel"},
            ...quotedStrings([`"`, `'`], popState(2)),
            {
              token: "alias",
              regex: /(^|\W?)\w+/,
              next: "pop",
            },
          ],
        },
      ],
      "string": quotedStrings([`"`, "'", "`"]),
      "spel": [ {
        token: "spel.start",
        regex: /#\{/,
        push: [
          {
            token: "spel.end",
            regex: /\}/,
            next: "pop",
          },
          {include: "spel-start"},
        ]
      } ],
      "functions": [
        {
          // TO_CHAR() | custom()
          token: "support.function.start",
          regex: `(${builtInFnStart}|${fnStart})`,
          push: [
            {
              token: "support.function.end",
              regex: /\)/,
              next: "pop",
            },
            {include: "start"},
          ],
        },
      ],
      "parens": [{
        token: "paren.start",
        regex: /\(/,
        push: [
          {
            token: "paren.end",
            regex: /\)/,
            next: "pop",
          },
          {include: "start"},
        ],
      }],
      "comments": [
        {token: "comment", regex: "--.*$"},
        {token: "comment", start: "/\\*", end: "\\*/"},
      ],
      "start" : [
        {include: "spel"},
        {include: "comments"},
        {include: "functions"},
        {include: "parens"},
        {include: "alias"},
        {include: "string"},
        {
          token: "constant.numeric", // float
          regex: "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b",
        },
        keywordRule,
        {
          token: "keyword.operator",
          regex: "\\+|\\-|\\/|\\/\\/|%|<@>|@>|<@|&|\\^|~|<|>|<=|=>|==|!=|<>|=",
        },
        {token: "paren.lparen", regex: "[\\(]"},
        {token: "paren.rparen", regex: "[\\)]"},
        {token: "text", regex: "\\s+"}]
    };

    this.embedRules(SpelHighlightRules, "spel-")
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
