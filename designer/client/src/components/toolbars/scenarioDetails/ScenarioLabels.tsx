import { useDispatch, useSelector } from "react-redux";
import { getScenarioLabels, getScenarioLabelsErrors } from "../../../reducers/selectors/graph";
import { Autocomplete, Box, Chip, Link, styled, SxProps, TextField, Theme, Typography, useTheme, createFilterOptions } from "@mui/material";
import { selectStyled } from "../../../stylesheets/SelectStyled";
import React, { ForwardRefExoticComponent, useCallback, useMemo, useState } from "react";
import HttpService from "../../../http/HttpService";
import i18next from "i18next";
import { editScenarioLabels } from "../../../actions/nk";
import { debounce } from "lodash";
import { ScenarioLabelValidationError } from "../../Labels/types";

interface Props {
    readOnly: boolean;
}

export type LabelsEdit = React.ComponentType<Props> | ForwardRefExoticComponent<Props>;

const AddLabel = ({ onClick }) => {
    return (
        <Typography
            component={Link}
            variant={"caption"}
            sx={(theme) => ({ cursor: "pointer", textDecoration: "none" })}
            onClick={(e) => onClick()}
        >
            {i18next.t("panels.scenarioDetails.addScenarioLabel", "+ Add label")}
        </Typography>
    );
};

const StyledAutocomplete = styled(Autocomplete)<{ isEdited: boolean }>(({ isEdited, theme }) => ({
    ".MuiFormControl-root": {
        margin: 0,
        "flex-direction": "column",
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

export const ScenarioLabels: LabelsEdit = ({ readOnly }: Props) => {
    const scenarioLabels = useSelector(getScenarioLabels);
    const theme = useTheme();
    const { menuOption } = selectStyled(theme);
    const dispatch = useDispatch();

    const [isFetching, setIsFetching] = useState(false);
    const [isOpen, setIsOpen] = useState(false);
    const [isEdited, setIsEdited] = useState(false);
    const [showLabelsEditor, setShowLabelsEditor] = useState(scenarioLabels.length != 0);
    const [input, setInput] = useState("");
    const [options, setOptions] = useState<string[]>([]);

    const initialLabelErrors = useSelector(getScenarioLabelsErrors).filter((e) => scenarioLabels.includes(e.label));
    console.log(`Initial label errors: ${JSON.stringify(initialLabelErrors)}`);
    const [labelsTyping, setLabelTyping] = useState(false);
    const [labelsErrors, setLabelsErrors] = useState<ScenarioLabelValidationError[]>(initialLabelErrors);

    console.log(`Label errors: ${JSON.stringify(labelsErrors)}`);

    const handleAddLabelClick = () => {
        setShowLabelsEditor(true);
        setIsEdited(true);
    };

    const inputHelperText = () => {
        if (labelsTyping && !isOpen) {
            return "Typing...";
        } else if (labelsErrors.filter((x) => scenarioLabels.includes(x.label)).length != 0) {
            return labelsErrors
                .map((e) => `Incorrect value '${e.label}': ${e.messages.join(",")}`)
                .map((message) => (
                    <>
                        {message}
                        <br />
                    </>
                ));
        } else {
            return undefined;
        }
    };

    const validateLabels = useMemo(() => {
        return debounce(async (newLabel: string) => {
            const labels = newLabel.length != 0 ? [...scenarioLabels, newLabel] : scenarioLabels;
            console.log(`Validating labels ${labels}`);
            const response = await HttpService.validateScenarioLabels(labels);

            if (response.status === 200) {
                setLabelsErrors(response.data.validationErrors);
            }

            setLabelTyping(false);
        }, 1);
    }, [scenarioLabels]);

    const fetchLabels = useCallback(async () => {
        setIsFetching(true);
        const { data } = await HttpService.fetchScenarioLabels();
        setIsFetching(false);
        return data.labels;
    }, []);

    const setLabels = (labels: string[]) => {
        const sortedLabels = labels.sort((a, b) => a.localeCompare(b));
        dispatch(editScenarioLabels(sortedLabels));
        console.log(`Scenario labels: ${scenarioLabels}`);
    };

    return (
        <>
            {!showLabelsEditor ? (
                <AddLabel onClick={handleAddLabelClick} />
            ) : (
                <StyledAutocomplete
                    isEdited={isEdited}
                    id="scenario-labels"
                    clearOnBlur={true}
                    disabled={readOnly}
                    disableClearable={!isEdited}
                    disableCloseOnSelect={false}
                    freeSolo
                    filterSelectedOptions={true}
                    inputValue={input}
                    loading={isFetching}
                    multiple
                    noOptionsText={i18next.t("panels.scenarioDetails.noAvailableScenarioLabels", "No labels")}
                    onBlur={() => {
                        setIsEdited(false);
                        if (scenarioLabels.length == 0) {
                            setShowLabelsEditor(false);
                        }
                    }}
                    onChange={(e, labels) => {
                        console.log("On change");
                        setLabels(labels);
                    }}
                    onClose={() => {
                        setIsOpen(false);
                    }}
                    onFocus={() => {
                        console.log("On focus");
                        setIsEdited(true);
                        validateLabels(input);
                    }}
                    onInputChange={(event, newInputValue: string) => {
                        console.log("on input change");
                        setLabelTyping(true);
                        setLabelsErrors([]);
                        validateLabels(newInputValue);
                        setInput(newInputValue);
                    }}
                    onOpen={async () => {
                        const fetchedAvailableLabels = await fetchLabels();
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
                                error={labelsErrors.length != 0}
                                helperText={inputHelperText()}
                                inputProps={{
                                    ...params.inputProps,
                                    onKeyDown: (e) => {
                                        if (e.key === "Enter" && (labelsErrors.length != 0 || labelsTyping)) {
                                            e.stopPropagation();
                                        }
                                    },
                                }}
                            />
                        </div>
                    )}
                    renderOption={(props, option) => {
                        return (
                            <Box component={"li"} sx={menuOption({}, false, false) as SxProps<Theme>} {...props} aria-selected={false}>
                                {option}
                            </Box>
                        );
                    }}
                    renderTags={(value: readonly string[], getTagProps) => {
                        return value.map((option: string, index: number) => {
                            const { key, ...tagProps } = getTagProps({ index });
                            const props = isEdited ? { ...tagProps } : {};
                            const labelError = labelsErrors.find((e) => e.label == option);
                            return (
                                <Chip
                                    key={key}
                                    color={labelError ? "error" : "default"}
                                    size="small"
                                    variant="outlined"
                                    disabled={readOnly}
                                    label={option}
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
