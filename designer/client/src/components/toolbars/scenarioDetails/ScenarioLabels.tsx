import { useDispatch, useSelector } from "react-redux";
import { getScenario, getScenarioLabels } from "../../../reducers/selectors/graph";
import { Autocomplete, Box, Link, styled, SxProps, TextField, Theme, Typography, useTheme } from "@mui/material";
import { selectStyled } from "../../../stylesheets/SelectStyled";
import React, {ForwardRefExoticComponent, useCallback, useMemo, useState} from "react";
import HttpService from "../../../http/HttpService";
import i18next from "i18next";
import { Chip } from "@mui/material";
import { editScenarioLabels } from "../../../actions/nk";
import {getFeatureSettings, getSettings} from "../../../reducers/selectors/settings";
import {createSelector} from "reselect";
import {NodeValidationError} from "../../../types";
import {debounce} from "lodash";
import {GenericValidationRequest} from "../../../actions/nk/genericAction";
import {ExpressionLang} from "../../graph/node-modal/editors/expression/types";
import {ScenarioLabelValidationError} from "../../Labels/types";

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
        "flex-direction": "column"
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
    const featureSettings = useSelector(getFeatureSettings)
    const theme = useTheme();
    const { menuOption } = selectStyled(theme);
    const dispatch = useDispatch();

    const [isFetching, setIsFetching] = useState(false);
    const [isOpen, setIsOpen] = useState(false);
    const [isEdited, setIsEdited] = useState(false);
    const [showLabelsEditor, setShowLabelsEditor] = useState(scenarioLabels.length != 0);
    const [input, setInput] = useState('');
    const [options, setOptions] = useState<string[]>([]);

    const [temporaryListItem, setTemporaryListItem] = useState("");
    const [temporaryValuesTyping, setTemporaryValuesTyping] = useState(false);
    const [temporaryValueErrors, setTemporaryValueErrors] = useState<ScenarioLabelValidationError[]>([]);

    const handleAddLabelClick = () => {
        setShowLabelsEditor(true);
        setIsEdited(true);
    };

    const helperText = 'Helper text'
    const validationRegex = useMemo(() => {
            return new RegExp(featureSettings.scenarioLabelSettings?.validationPattern || '.*');
        },
        [featureSettings.scenarioLabelSettings]
    )

    const validateTemporaryListItem = useMemo(() => {
        return debounce(async (expressionVariable: string) => {

            const response = await HttpService.validateScenarioLabels(scenarioLabels);

            if (response.status === 200) {
                setTemporaryValueErrors(response.data.errors);
            }

            setTemporaryValuesTyping(false);
        }, 500);
    }, []);

    const handleInputChange = (event, newInputValue: string) => {
        console.log(`handle input change with value ${newInputValue}`);
        setTemporaryListItem(newInputValue);
        setTemporaryValuesTyping(true);
        setTemporaryValueErrors([]);
        validateTemporaryListItem(newInputValue);
        setInput(newInputValue);

        // if (!(newInputValue.length == 0 && validationRegex.test(input))) {
        //     setInput(newInputValue);
        // }
    };

    const isInputValid = () : boolean => {
        return input.length == 0 || validationRegex.test(input)
    }

    // todo debounce or refresh to limit calls
    const fetchLabels = useCallback(async () => {
        setIsFetching(true);
        const { data } = await HttpService.fetchScenarioLabels();
        setIsFetching(false);
        return data.labels;
    }, []);

    const saveLabels = (labels: string[]) => {
        console.log(`handling save labels with input: '${input}'`)
        const sortedLabels = labels.sort((a, b) => a.localeCompare(b));
        dispatch(editScenarioLabels(sortedLabels));
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
                        console.log("on change")
                        saveLabels(labels);
                    }}
                    onClose={() => {
                        setIsOpen(false);
                    }}
                    onFocus={() => {
                        setIsEdited(true);
                    }}
                    onInputChange={
                        handleInputChange
                    }
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
                                error={!isInputValid()}
                                helperText={temporaryValuesTyping && "Typing..."}
                                inputProps={{
                                    ...params.inputProps,
                                    onKeyDown: (e) => {
                                        if (e.key === 'Enter' && !isInputValid()) {
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
                            return <Chip key={key} size="small" variant="outlined" disabled={readOnly} label={option} {...props} />;
                        });
                    }}
                    size="small"
                    value={scenarioLabels}
                />
            )}
        </>
    );
};
