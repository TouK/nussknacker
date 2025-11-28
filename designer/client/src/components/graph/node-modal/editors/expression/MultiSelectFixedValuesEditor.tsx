import i18next from "i18next";

import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { EditorType } from "./types";

export const MultiSelectFixedValuesEditor = prepareEditor(
    (props) => {
        return null;
    },
    {
        isSwitchableTo: (expressionObj, editorConfig) => {
            return true;
        },
        notSwitchableToHint: () =>
            i18next.t(
                "editors.multiSelectFixedValues.notSwitchableToHint",
                "Expression must contain only predefined values to switch to {{editorName}} mode",
                { editorName: editorsParameters[EditorType.MULTI_SELECT_EDITOR].displayName },
            ),
    },
);
