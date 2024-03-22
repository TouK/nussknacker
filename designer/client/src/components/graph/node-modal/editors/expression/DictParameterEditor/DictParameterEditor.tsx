import React, { useCallback, useMemo, useState } from "react";
import { Autocomplete, Box, SxProps, Theme, useTheme } from "@mui/material";
import HttpService, { ProcessDefinitionDataDictOption } from "../../../../../../http/HttpService";
import { getScenario } from "../../../../../../reducers/selectors/graph";
import { useSelector } from "react-redux";
import { debounce } from "@mui/material/utils";
import { SimpleEditor } from "../Editor";
import { ExpressionObj } from "../types";
import { FieldError } from "../../Validators";
import { ParamType } from "../../types";
import { NodeInput } from "../../../../../FormElements";
import { selectStyled } from "../../../../../../stylesheets/SelectStyled";
import i18next from "i18next";
import ValidationLabels from "../../../../../modals/ValidationLabels";
import { cx } from "@emotion/css";
import { isEmpty } from "lodash";

interface Props {
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
    fieldErrors: FieldError[];
    param: ParamType;
    showValidation: boolean;
    readOnly: boolean;
}

export const DictParameterEditor: SimpleEditor<Props> = ({
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
    const [value, setValue] = useState(() => {
        if (!expressionObj.expression) {
            return null;
        }

        return JSON.parse(expressionObj.expression);
    });
    const [inputValue, setInputValue] = useState("");
    const [isFetching, setIsFetching] = useState(false);

    const fetchProcessDefinitionDataDict = useCallback(
        async (inputValue: string) => {
            setIsFetching(true);
            const { data } = await HttpService.fetchProcessDefinitionDataDict(scenario.processingType, param.editor.dictId, inputValue);

            setIsFetching(false);

            return data;
        },
        [param.editor.dictId, scenario.processingType],
    );

    const debouncedUpdateOptions = useMemo(() => {
        return debounce(async (value: string) => {
            const fetchedOptions = await fetchProcessDefinitionDataDict(value);
            setOptions(fetchedOptions);
        }, 400);
    }, [fetchProcessDefinitionDataDict]);

    const isValid = isEmpty(fieldErrors);

    return (
        <Box className={"node-value"}>
            <Autocomplete
                renderInput={({ inputProps, InputProps }) => (
                    <div ref={InputProps.ref}>
                        <NodeInput
                            {...inputProps}
                            className={cx(
                                "node-input",
                                inputProps.className,
                                showValidation && !isValid && "node-input-with-error",
                                readOnly && "read-only",
                            )}
                        />
                    </div>
                )}
                options={options}
                filterOptions={(x) => x}
                onChange={(_, value) => {
                    onValueChange(JSON.stringify(value) || "");
                    setValue(value);
                    setOpen(false);
                }}
                onOpen={async () => {
                    const fetchedOptions = await fetchProcessDefinitionDataDict(inputValue);
                    setOptions(fetchedOptions);
                    setOpen(true);
                }}
                onClose={() => {
                    setOpen(false);
                }}
                open={open}
                noOptionsText={i18next.t("editors.dictParameterEditor.noOptionsFound", "No options found")}
                getOptionLabel={(option) => option.label}
                isOptionEqualToValue={() => true}
                value={value}
                inputValue={inputValue}
                loading={isFetching}
                renderOption={(props, option) => {
                    return (
                        <Box component={"li"} sx={menuOption({}, false, false) as SxProps<Theme>} {...props} aria-selected={false}>
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
