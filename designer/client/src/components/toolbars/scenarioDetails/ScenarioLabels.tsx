import { useDispatch, useSelector } from "react-redux";
import { getScenarioLabels, getScenarioLabelsErrors } from "../../../reducers/selectors/graph";
import {
    Autocomplete,
    AutocompleteInputChangeReason,
    Box,
    Chip,
    createFilterOptions,
    styled,
    SxProps,
    TextField,
    Theme,
    Typography,
    useTheme,
} from "@mui/material";
import { selectStyled } from "../../../stylesheets/SelectStyled";
import React, { ForwardRefExoticComponent, useCallback, useEffect, useMemo, useState } from "react";
import HttpService from "../../../http/HttpService";
import i18next from "i18next";
import { editScenarioLabels } from "../../../actions/nk";
import { debounce } from "lodash";
import { ScenarioLabelValidationError } from "../../Labels/types";

interface Props {
    readOnly: boolean;
}

export type LabelsEdit = React.ComponentType<Props> | ForwardRefExoticComponent<Props>;

const AddLabel = ({ onClick, theme: Theme }) => {
    return (
        <Typography
            component={Box}
            variant={"caption"}
            sx={(theme) => ({ cursor: "pointer", textDecoration: "none", color: theme.palette.text.primary })}
            onClick={(e) => onClick()}
        >
            {i18next.t("panels.scenarioDetails.labels.addLabelTitle", "+ Add label")}
        </Typography>
    );
};

const StyledAutocomplete = styled(Autocomplete)<{ isEdited: boolean }>(({ isEdited, theme }) => ({
    ".MuiFormControl-root": {
        margin: 0,
        flexDirection: "column",
    },
    ...{
        ...(!isEdited && {
            "&:hover": {
                backgroundColor: theme.palette.action.hover,
            },
            ".MuiInputBase-input": {
                outline: "none",
            },
            ".MuiOutlinedInput-notchedOutline": {
                border: "none",
            },
        }),
    },
}));

const filter = createFilterOptions();

type LabelOption = {
    title: string;
    value: string;
    inputValue?: string;
};

function toLabelOption(value: string): LabelOption {
    return {
        title: value,
        value: value,
    };
}

function toLabelValue(option: LabelOption): string {
    return option.value;
}

function formatErrors(errors: ScenarioLabelValidationError[]): string {
    return errors.map((error) => `Incorrect value '${error.label}': ${error.messages.join(",")}`).join("\n");
}

export const ScenarioLabels: LabelsEdit = ({ readOnly }: Props) => {
    const scenarioLabels: LabelOption[] = useSelector(getScenarioLabels).map(toLabelOption);
    const initialLabelErrors = useSelector(getScenarioLabelsErrors).filter((error) =>
        scenarioLabels.some((label) => toLabelValue(label) === error.label),
    );
    const [labelsErrors, setLabelsErrors] = useState<ScenarioLabelValidationError[]>(initialLabelErrors);
    const [showEditor, setShowEditor] = useState(scenarioLabels.length !== 0);

    const theme = useTheme();
    const { menuOption } = selectStyled(theme);
    const dispatch = useDispatch();

    const [isFetching, setIsFetching] = useState(false);
    const [isOpen, setIsOpen] = useState(false);
    const [isEdited, setIsEdited] = useState(false);
    const [options, setOptions] = useState<LabelOption[]>([]);

    const [input, setInput] = useState("");
    const [inputTyping, setInputTyping] = useState(false);
    const [inputErrors, setInputErrors] = useState<ScenarioLabelValidationError[]>([]);

    const handleAddLabelClick = () => {
        setShowEditor(true);
        setIsEdited(true);
    };

    const inputHelperText: () => string = () => {
        if (inputErrors.length !== 0) {
            return formatErrors(inputErrors.concat(labelsErrors));
        }

        if (!isOpen && labelsErrors.length !== 0) {
            return formatErrors(labelsErrors);
        }

        return undefined;
    };

    const validateInput = useMemo(() => {
        return debounce(async (newInput: string) => {
            if (newInput !== "") {
                const response = await HttpService.validateScenarioLabels([newInput]);

                if (response.status === 200) {
                    setInputErrors(response.data.validationErrors);
                }
            }

            setInputTyping(false);
        }, 500);
    }, []);

    const validateLabels = useMemo(() => {
        return debounce(async (labels: LabelOption[]) => {
            const values = labels.map(toLabelValue);
            const response = await HttpService.validateScenarioLabels(values);

            if (response.status === 200) {
                const validationError = response.data.validationErrors;
                setLabelsErrors(validationError);
            }
        }, 500);
    }, []);

    const fetchAvailableLabels = useCallback(async () => {
        setIsFetching(true);
        const { data } = await HttpService.fetchScenarioLabels();
        setIsFetching(false);
        return data.labels.map(toLabelOption);
    }, []);

    const setLabels = (labels: LabelOption[]) => {
        const sortedLabels = labels.map(toLabelValue).sort((a, b) => a.localeCompare(b));
        dispatch(editScenarioLabels(sortedLabels));
    };

    useEffect(() => {
        validateLabels(scenarioLabels);

        if (!isEdited && scenarioLabels.length === 0) {
            setShowEditor(false);
        }

        if (!showEditor && scenarioLabels.length !== 0) {
            setShowEditor(true);
        }
    }, [scenarioLabels]);

    return (
        <>
            {!showEditor ? (
                <AddLabel onClick={handleAddLabelClick} theme={theme} />
            ) : (
                <StyledAutocomplete
                    isEdited={isEdited}
                    id="scenario-labels"
                    disabled={readOnly}
                    disableClearable={!isEdited}
                    disableCloseOnSelect={false}
                    freeSolo
                    fullWidth
                    filterOptions={(options: (string | LabelOption)[], params) => {
                        const filtered = filter(options, params);

                        const { inputValue } = params;

                        const isExisting = options.some((option) => (typeof option === "string" ? false : inputValue === option.value));
                        if (inputValue !== "" && !isExisting && !inputTyping && inputErrors.length === 0) {
                            filtered.push({
                                inputValue,
                                title: `${i18next.t("panels.scenarioDetails.labels.addNewLabel", "Add label")} "${inputValue}"`,
                            });
                        }

                        return inputErrors.length !== 0 ? [] : filtered;
                    }}
                    filterSelectedOptions={true}
                    getOptionLabel={(option: string | LabelOption) => {
                        if (typeof option === "string") {
                            return option;
                        }
                        return option.title;
                    }}
                    getOptionKey={(option: string | LabelOption) => {
                        if (typeof option === "string") {
                            return option;
                        }
                        return toLabelValue(option);
                    }}
                    inputValue={input}
                    isOptionEqualToValue={(v1: LabelOption, v2: LabelOption) => v1.value === v2.value}
                    loading={isFetching || inputTyping}
                    loadingText={inputTyping ? "Typing..." : "Loading..."}
                    multiple
                    noOptionsText={i18next.t("panels.scenarioDetails.noAvailableScenarioLabels", "No labels")}
                    onBlur={() => {
                        setIsEdited(false);
                        setInput("");
                        setInputErrors([]);
                        if (scenarioLabels.length == 0) {
                            setShowEditor(false);
                        }
                    }}
                    onChange={(_, values: (string | LabelOption)[]) => {
                        const labelOptions = values.map((value) => {
                            if (typeof value === "string") {
                                return toLabelOption(value);
                            } else if (value && value.inputValue) {
                                return toLabelOption(value.inputValue);
                            } else {
                                return value;
                            }
                        });
                        setLabels(labelOptions);
                    }}
                    onClose={() => {
                        setIsOpen(false);
                    }}
                    onFocus={() => {
                        setIsEdited(true);
                    }}
                    onInputChange={(_, newInputValue: string, reason: AutocompleteInputChangeReason) => {
                        if (reason === "input") {
                            setInputTyping(true);
                        }
                        setInputErrors([]);
                        validateInput(newInputValue);
                        setInput(newInputValue);
                    }}
                    onOpen={async () => {
                        const fetchedAvailableLabels = await fetchAvailableLabels();
                        setOptions(fetchedAvailableLabels);
                        setIsOpen(true);
                    }}
                    open={isOpen}
                    options={options}
                    renderInput={(params) => (
                        <div ref={params.InputProps.ref}>
                            <TextField
                                size="small"
                                {...params}
                                variant="outlined"
                                autoFocus={isEdited}
                                error={labelsErrors.length !== 0 || inputErrors.length !== 0}
                                helperText={inputHelperText()}
                                inputProps={{
                                    ...params.inputProps,
                                    onKeyDown: (event) => {
                                        if (event.key === "Enter" && (inputErrors.length !== 0 || inputTyping)) {
                                            event.stopPropagation();
                                        }
                                    },
                                }}
                            />
                        </div>
                    )}
                    renderOption={(props, option, _, ownerState) => {
                        return (
                            <Box component={"li"} sx={menuOption({}, false, false) as SxProps<Theme>} {...props} aria-selected={false}>
                                {ownerState.getOptionLabel(option)}
                            </Box>
                        );
                    }}
                    renderTags={(value: (string | LabelOption)[], getTagProps) => {
                        return value
                            .filter((v) => typeof v !== "string")
                            .map((option: LabelOption, index: number) => {
                                const { key, ...tagProps } = getTagProps({ index });
                                const props = isEdited ? { ...tagProps } : {};
                                const labelError = labelsErrors.find((error) => error.label == toLabelValue(option));
                                return (
                                    <Chip
                                        key={key}
                                        color={labelError ? "error" : "default"}
                                        size="small"
                                        variant="outlined"
                                        disabled={readOnly}
                                        label={option.title}
                                        {...props}
                                    />
                                );
                            });
                    }}
                    size="small"
                    value={scenarioLabels}
                />
            )}
        </>
    );
};
