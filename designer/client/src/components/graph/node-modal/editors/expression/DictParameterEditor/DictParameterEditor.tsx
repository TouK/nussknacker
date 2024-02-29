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
import { NodeInput } from "../../../../../withFocus";
import { selectStyled } from "../../../../../../stylesheets/SelectStyled";
import i18next from "i18next";

interface Props {
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
    fieldErrors: FieldError[];
    param: ParamType;
}

export const DictParameterEditor: SimpleEditor<Props> = (props: Props) => {
    const scenario = useSelector(getScenario);
    const theme = useTheme();
    const { menuOption } = selectStyled(theme);
    const [options, setOptions] = useState<ProcessDefinitionDataDictOption[]>([]);
    const [open, setOpen] = useState(false);
    const [value, setValue] = React.useState(() => {
        if (!props.expressionObj.expression) {
            return null;
        }

        return JSON.parse(JSON.stringify(props.expressionObj.expression));
    });
    const [inputValue, setInputValue] = React.useState("");

    const fetchProcessDefinitionDataDict = useCallback(
        async (inputValue: string) => {
            const { data } = await HttpService.fetchProcessDefinitionDataDict(
                scenario.processingType,
                props.param.editor.dictId,
                inputValue,
            );

            return data;
        },
        [props.param.editor.dictId, scenario.processingType],
    );

    const debouncedUpdateOptions = useMemo(() => {
        return debounce(async (value: string) => {
            const fetchedOptions = await fetchProcessDefinitionDataDict(value);
            setOptions(fetchedOptions);
        }, 400);
    }, [fetchProcessDefinitionDataDict]);

    return (
        <Box className={"node-value"}>
            <Autocomplete
                renderInput={({ inputProps, InputProps }) => (
                    <div ref={InputProps.ref}>
                        <NodeInput {...inputProps} />
                    </div>
                )}
                options={options}
                filterOptions={(x) => x}
                onChange={(_, value) => {
                    props.onValueChange(value?.key || "");
                    setValue(value);
                    setOpen(false);
                }}
                onOpen={async () => {
                    const fetchedOptions = await fetchProcessDefinitionDataDict(inputValue);
                    setOptions(fetchedOptions);
                    setOpen(true);
                }}
                open={open}
                onBlur={() => {
                    setOpen(false);
                }}
                noOptionsText={i18next.t("editors.dictParameterEditor.noOptionsFOund", "No options found")}
                getOptionLabel={(option) => option.label}
                isOptionEqualToValue={() => true}
                value={value}
                inputValue={inputValue}
                renderOption={(props, option) => {
                    return (
                        <Box component={"li"} sx={menuOption({}, false, false) as SxProps<Theme>} {...props} aria-selected={false}>
                            {option.label}
                        </Box>
                    );
                }}
                onInputChange={async (event, value) => {
                    await debouncedUpdateOptions(value);
                    console.log(value);
                    setInputValue(value);
                }}
            />
        </Box>
    );
};
