//based on https://github.com/thlorenz/brace/blob/master/theme/monokai.js

const nussknackerCssTheme = require('!raw-loader!./nussknacker.css').default;

ace.define("ace/theme/nussknacker",["require","exports","module","ace/lib/dom"], function(acequire, exports, module) {

exports.isDark = true;
exports.cssClass = "ace-nussknacker";
exports.cssText = nussknackerCssTheme;

var dom = acequire("../lib/dom");
dom.importCssString(exports.cssText, exports.cssClass);
});
