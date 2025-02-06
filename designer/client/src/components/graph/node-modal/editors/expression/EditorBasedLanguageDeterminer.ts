import { ExpressionLang } from "./types";
import { EditorType } from "./Editor";

// This determiner is used to fix potential language inconsistencies due to incompatible changes to editors in fragments
// or components with AdditionalUIConfigProvider. It doesn't determine all editors as only a few of them can be affected
// by said inconsistencies.
export const determineLangaugeBasedOnEditor = (editor: $TodoType): ExpressionLang => {
    switch (editor?.type) {
        case EditorType.DICT_PARAMETER_EDITOR:
            return ExpressionLang.DictKeyWithLabel;
        case EditorType.FIXED_VALUES_PARAMETER_EDITOR:
            return ExpressionLang.SpEL;
        case EditorType.DUAL_PARAMETER_EDITOR:
            if (editor.simpleEditor.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR) return ExpressionLang.SpEL;
            if (editor.simpleEditor.type === EditorType.STRING_PARAMETER_EDITOR) return ExpressionLang.SpEL;
            return null;
        default:
            return null;
    }
};
