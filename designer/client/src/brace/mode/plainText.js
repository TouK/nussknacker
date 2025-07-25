ace.define("ace/mode/plain_text", ["require", "exports", "module", "ace/lib/oop", "ace/mode/text_highlight_rules"], function(acequire, exports, module) {
    "use strict";
    var oop = acequire("../lib/oop");
    var TextHighlightRules = acequire("./text_highlight_rules").TextHighlightRules;

    var CustomHighlightRules = function() {
        this.$rules = {
            "start": [
                { token: "string", regex: '".*?"' },
                { token: "numeric", regex: '\\b\\d+\\b' },
                { token: "boolean", regex: "\\b(?:true|false)\\b", caseInsensitive: true },


            ]
        };
    };
    oop.inherits(CustomHighlightRules, TextHighlightRules);
    exports.CustomHighlightRules = CustomHighlightRules;

    var Mode = function() {
        // Initialize the base text mode internals (behaviors, foldingRules, etc.)
        var TextMode = acequire("ace/mode/text").Mode;
        TextMode.call(this);

        // Override the highlight rules with your custom rules
        this.HighlightRules = CustomHighlightRules;
    };
    oop.inherits(Mode, acequire("ace/mode/text").Mode);

    exports.Mode = Mode;
});
