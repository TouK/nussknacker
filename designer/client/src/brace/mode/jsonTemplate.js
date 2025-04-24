ace.define("ace/mode/jsonTemplate_highlight_rules", ["require", "exports", "module", "ace/lib/oop", "ace/mode/json_highlight_rules", "ace/mode/spel_highlight_rules"], function (acequire, exports, module) {
    "use strict";

    var oop = acequire("../lib/oop");
    var JsonHighlightRules = acequire("ace/mode/json_highlight_rules").JsonHighlightRules;
    var SpelHighlightRules = acequire("./spel_highlight_rules").SpelHighlightRules;

    var JsonTemplateHighlightRules = function () {
        // 1) Inherit all of the built‑in JSON rules
        JsonHighlightRules.call(this);

        // 2) Tweak the “property” rule so it DOESN’T match keys containing #{…}
        let startRules = this.$rules.start;
        let propRule = startRules.find(r => r.token === "variable");
        if (propRule) {
          // negative lookahead (?![^"]*#\{) means “no #{ anywhere before the closing quote”
          propRule.regex = '"(?![^"]*#\\{)(?:\\\\.|[^\\\\"])*"(?=\\s*:)';
        }

        // 3) Inject template opener into both root AND string states
        let opener = {
            token: "meta.block-marker.jsonTemplate",
            regex: /#\{/,
            push: "jsonTemplate-start"
        };

        // 3) …and inject it *both* into the root and into the JSON-string state
        this.$rules.start.unshift(opener);
        this.$rules.string.unshift(opener);

        // 4) Existing SpEL embed‑and‑end logic
        let endRules = [{
            token: "meta.block-marker.jsonTemplate",
            regex: /[{}]/,
            onMatch: function (val, state, stack) {
                if(state?.startsWith("jsonTemplate-string")) {
                    return "string";
                }
                this.next = "";
                if (val === "{") {
                    stack.unshift("jsonTemplate-start", state);
                    return "paren";
                }
                stack.shift();
                this.next = stack.shift() || "start";
                return this.next === "jsonTemplate-start" ? "paren" : "meta.block-marker.jsonTemplate";

            }
        }];

        // 5) Finally wire in the SPEL/template rules
        this.embedRules(SpelHighlightRules, "jsonTemplate-", endRules);
        this.normalizeRules();
    };

    oop.inherits(JsonTemplateHighlightRules, JsonHighlightRules);

    exports.JsonTemplateHighlightRules = JsonTemplateHighlightRules;

});

ace.define("ace/mode/jsonTemplate", ["require", "exports", "ace/mode/jsonTemplate_highlight_rules", "ace/mode/spel", "ace/mode/json"], function (acequire, exports, module) {
    "use strict";
    var oop = acequire("../lib/oop");
    let JsonMode = acequire("ace/mode/json").Mode;
    let JsonTemplateHighlightRules = acequire("ace/mode/jsonTemplate_highlight_rules").JsonTemplateHighlightRules;

    var Mode = function () {
        // initialize all of JSON mode's internals (behaviors, foldingRules, etc.)
        JsonMode.call(this);
        // then override just the HighlightRules
        this.HighlightRules = JsonTemplateHighlightRules;
    };
    oop.inherits(Mode, JsonMode);
    exports.Mode = Mode;
});
