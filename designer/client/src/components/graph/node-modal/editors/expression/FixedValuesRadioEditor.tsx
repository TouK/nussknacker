import { cx } from "@emotion/css";
import { FormControlLabel, Radio, RadioGroup } from "@mui/material";
import i18next from "i18next";
import { isEmpty } from "lodash";
import React from "react";

import { prepareEditor } from "./Editor";
import type { EditorConfigForType } from "./EditorConfig";
import { editorsParameters } from "./editorsParameters";
import { getOptions, handleCurrentOption, type Option } from "./FixedValuesEditor";
import { EditorType } from "./types";

export type FixedValuesRadioEditorProps = {
    editorConfig: EditorConfigForType<EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR>;
    param?: $TodoType; // TODO: really used?
};

export const FixedValuesRadioEditor = prepareEditor<FixedValuesRadioEditorProps>(
    ({ className, editorConfig, expressionObj, onValueChange, param }) => {
        return (
            <div className={cx(className)}>
                <RadioGroup
                    value={handleCurrentOption(expressionObj, getOptions(editorConfig.possibleValues)).value}
                    onChange={(event) =>
                        onValueChange({
                            expression: event.target.value,
                            language: editorsParameters[EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR].language,
                        })
                    }
                >
                    {getOptions(editorConfig.possibleValues).map((option: Option) => {
                        const label = option.value === param?.defaultValue ? `${option.label} (default)` : option.label;
                        return <FormControlLabel key={option.value} value={option.value} control={<Radio />} label={label} />;
                    })}
                </RadioGroup>
            </div>
        );
    },
    {
        isSwitchableTo: (expressionObj, editorConfig) =>
            editorConfig.possibleValues.map((v) => v.expression).includes(expressionObj.expression) || isEmpty(expressionObj.expression),
        notSwitchableToHint: () =>
            i18next.t(
                "editors.fixedValues.notSwitchableToHint",
                "Expression must be one of the predefined values to switch to {{editorName}} mode",
                { editorName: editorsParameters[EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR].displayName },
            ),
    },
);
