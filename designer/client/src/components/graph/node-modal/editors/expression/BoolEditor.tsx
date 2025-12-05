import { FormControlLabel, Switch } from "@mui/material";
import i18next from "i18next";
import { isEmpty } from "lodash";
import React from "react";

import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { EditorType, ExpressionLang } from "./types";

const TRUE_EXPRESSION = "true";
const FALSE_EXPRESSION = "false";

const parseable = ({ expression, language }) => {
    return [TRUE_EXPRESSION, FALSE_EXPRESSION].includes(expression) && language === ExpressionLang.SpEL;
};

export const BoolEditor = prepareEditor(
    ({ expressionObj, onValueChange, readOnly }) => {
        return (
            <FormControlLabel
                disabled={readOnly}
                control={<Switch />}
                label={null}
                checked={expressionObj?.expression === TRUE_EXPRESSION}
                onChange={(e, checked) => {
                    onValueChange({
                        language: ExpressionLang.SpEL,
                        expression: checked ? TRUE_EXPRESSION : FALSE_EXPRESSION,
                    });
                }}
            />
        );
    },
    {
        isSwitchableTo: (expressionObj) => parseable(expressionObj) || isEmpty(expressionObj.expression),
        notSwitchableToHint: () =>
            i18next.t("editors.bool.notSwitchableToHint", "Expression must be equal to true or false to switch to {{displayName}} mode", {
                displayName: editorsParameters[EditorType.BOOL_PARAMETER_EDITOR].displayName,
            }),
    },
);
