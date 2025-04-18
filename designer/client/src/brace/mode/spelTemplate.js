ace.define("ace/mode/spelTemplate_highlight_rules", ["require", "exports", "module", "ace/lib/oop", "ace/mode/text_highlight_rules", "ace/mode/json_highlight_rules", "ace/mode/spel_highlight_rules"], function (acequire, exports, module) {
    "use strict";

    var oop = acequire("../lib/oop");
    var TextHighlightRules = acequire("ace/mode/text_highlight_rules").TextHighlightRules;
    var SpelHighlightRules = acequire("./spel_highlight_rules").SpelHighlightRules;
    var JsonHighlightRules = acequire("ace/mode/json_highlight_rules").JsonHighlightRules;

    var SpelTemplateHighlightRules = function () {
        JsonHighlightRules.call(this);

        this.$rules = {
            start: [{
                token: "meta.block-marker.spelTemplate",
                regex: "#\{",
                next: "spelTemplate-start"

            }]
        };

        let endRules = [{
            token: "meta.block-marker.spelTemplate",
            regex: RegExp("([{}])"),
            onMatch: function (val, state, stack) {
                if(state?.startsWith("spelTemplate-string")) {
                    return "string";
                }
                this.next = "";
                if (val === "{") {
                    stack.unshift("spelTemplate-start", state);
                    return "paren";
                }
                stack.shift();
                this.next = stack.shift() || "start";
                return this.next === "spelTemplate-start" ? "paren" : "meta.block-marker.spelTemplate";

            }
        }]

        this.embedRules(SpelHighlightRules, "spelTemplate-", endRules);
        this.normalizeRules();
    };

    oop.inherits(SpelTemplateHighlightRules, JsonHighlightRules);

    exports.SpelTemplateHighlightRules = SpelTemplateHighlightRules;

});

ace.define("ace/mode/spelTemplate", ["require", "exports", "ace/mode/spelTemplate_highlight_rules", "ace/mode/spel", "ace/mode/text", "ace/mode/json"], function (acequire, exports, module) {
    "use strict";
    var oop = acequire("../lib/oop");
    let TextMode = acequire("ace/mode/text").Mode;
    let JsonMode = acequire("ace/mode/json").Mode;

    let SpelTemplateHighlightRules = acequire("ace/mode/spelTemplate_highlight_rules").SpelTemplateHighlightRules;

    var Mode = function () {
        JsonMode.call(this);
        this.HighlightRules = SpelTemplateHighlightRules;
    };
    // oop.inherits(Mode, TextMode);
    oop.inherits(Mode, JsonMode);
    exports.Mode = Mode;
});
