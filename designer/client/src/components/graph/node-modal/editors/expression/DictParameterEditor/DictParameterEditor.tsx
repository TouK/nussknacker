import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Autocomplete, Box, SxProps, Theme, useTheme } from "@mui/material";
import HttpService, { ProcessDefinitionDataDictOption } from "../../../../../../http/HttpService";
import { getScenario } from "../../../../../../reducers/selectors/graph";
import { useSelector } from "react-redux";
import { debounce } from "@mui/material/utils";
import { ExtendedEditor } from "../Editor";
import { ExpressionLang, ExpressionObj } from "../types";
import { FieldError } from "../../Validators";
import { ParamType } from "../../types";
import { NodeInput } from "../../../../../FormElements";
import { selectStyled } from "../../../../../../stylesheets/SelectStyled";
import i18next from "i18next";
import ValidationLabels from "../../../../../modals/ValidationLabels";
import { cx } from "@emotion/css";
import { isEmpty } from "lodash";
import { tryParseOrNull } from "../../../../../../common/JsonUtils";
import { nodeInput, nodeInputWithError, nodeValue } from "../../../NodeDetailsContent/NodeTableStyled";

interface Props {
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
    fieldErrors: FieldError[];
    param: ParamType;
    showValidation: boolean;
    readOnly: boolean;
}

export const DictParameterEditor: ExtendedEditor<Props> = ({
    fieldErrors,
    expressionObj,
    param,
    onValueChange,
    showValidation,
    readOnly,
}: Props) => {
    const scenario = useSelector(getScenario);
    const theme = useTheme();
    const { menuOption } = selectStyled(theme);
    const [options, setOptions] = useState<ProcessDefinitionDataDictOption[]>([]);
    const [open, setOpen] = useState(false);
    const [value, setValue] = useState<ProcessDefinitionDataDictOption>();
    const [inputValue, setInputValue] = useState("");
    const [isFetching, setIsFetching] = useState(false);

    const dictId = param.editor.dictId || param.editor?.simpleEditor?.dictId;

    const fetchProcessDefinitionDataDict = useCallback(
        async (inputValue: string) => {
            setIsFetching(true);
            const { data } = await HttpService.fetchProcessDefinitionDataDict(scenario.processingType, dictId, inputValue);

            setIsFetching(false);

            return data;
        },
        [dictId, scenario.processingType],
    );

    const fetchProcessDefinitionDataDictByKey = useCallback(
        async (key: string) => {
            setIsFetching(true);
            const response = await HttpService.fetchProcessDefinitionDataDictByKey(scenario.processingType, dictId, key);
            setIsFetching(false);
            return response;
        },
        [dictId, scenario.processingType],
    );

    const debouncedUpdateOptions = useMemo(() => {
        return debounce(async (value: string) => {
            const fetchedOptions = await fetchProcessDefinitionDataDict(value);
            setOptions(fetchedOptions);
        }, 400);
    }, [fetchProcessDefinitionDataDict]);

    const isValid = isEmpty(fieldErrors);

    // This logic is needed, because scenario is initially loaded without full validation data.
    // In that case the label is missing, and we need to fetch it separately.
    useEffect(() => {
        if (!expressionObj.expression) return;
        const parseObject = tryParseOrNull(expressionObj.expression);
        if (!parseObject) return;
        fetchProcessDefinitionDataDictByKey(parseObject?.key).then((response) => {
            if (response.status == "success") {
                setValue(response.data);
            } else {
                setValue(parseObject);
            }
        });
    }, [expressionObj, fetchProcessDefinitionDataDictByKey]);

    // This condition means, that we should delay rendering this fragment when both conditions are met:
    // - expression is defined, so we know that value is present, but we do not yet have enough information to render it (label)
    // - value is not yet available - label is not yet loaded
    if (!value && expressionObj?.expression) {
        return;
    }

    return (
        <Box className={nodeValue}>
            <Autocomplete
                disabled={readOnly}
                renderInput={({ inputProps, InputProps }) => (
                    <div ref={InputProps.ref}>
                        <NodeInput
                            {...inputProps}
                            className={cx(
                                nodeInput,
                                inputProps.className,
                                showValidation && !isValid && nodeInputWithError,
                                readOnly && "read-only",
                            )}
                        />
                    </div>
                )}
                options={options}
                filterOptions={(x) => x}
                onChange={(_, value) => {
                    onValueChange(value ? JSON.stringify(value) : "");
                    setValue(value);
                    setOpen(false);
                }}
                onOpen={async () => {
                    // On open we show all the options
                    const fetchedOptions = await fetchProcessDefinitionDataDict("");
                    setOptions(fetchedOptions);
                    setOpen(true);
                }}
                onClose={() => {
                    setOpen(false);
                }}
                open={open}
                noOptionsText={i18next.t("editors.dictParameterEditor.noOptionsFound", "No options found")}
                getOptionLabel={(option) => option.label ?? ""}
                isOptionEqualToValue={() => true}
                value={value}
                inputValue={inputValue}
                loading={isFetching}
                renderOption={(props, option) => {
                    const isSelected = option.key === value?.key;
                    return (
                        // aira-selected is set to false as it overrides styles defined in our menuOption
                        <Box component={"li"} sx={menuOption({}, isSelected, false) as SxProps<Theme>} {...props} aria-selected={false}>
                            {option.label}
                        </Box>
                    );
                }}
                onInputChange={async (event, value) => {
                    await debouncedUpdateOptions(value);
                    setInputValue(value);
                }}
            />
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
        </Box>
    );
};

const isParseable = (expressionObj: ExpressionObj) =>
    tryParseOrNull(expressionObj.expression) && typeof tryParseOrNull(expressionObj.expression) === "object";

DictParameterEditor.switchableToHint = () => i18next.t("editors.dictParameter.switchableToHint", "Switch to basic mode");
DictParameterEditor.notSwitchableToHint = () => i18next.t("editors.dictParameter.notSwitchableToHint", "");
DictParameterEditor.isSwitchableTo = () => true;
DictParameterEditor.getExpressionMode = (expressionObj) => ({
    language: ExpressionLang.SpEL,
    expression: isParseable(expressionObj) ? "" : expressionObj.expression,
});
DictParameterEditor.getBasicMode = (expressionObj) => ({
    language: ExpressionLang.DictKeyWithLabel,
    expression: isParseable(expressionObj) ? expressionObj.expression : "",
});
