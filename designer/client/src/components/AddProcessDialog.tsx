import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { visualizationUrl } from "../common/VisualizationUrl";
import { useProcessNameValidators } from "../containers/hooks/useProcessNameValidators";
import HttpService, { ScenarioParametersCombination } from "../http/HttpService";
import { WindowContent } from "../windowManager";
import { AddProcessForm, FormValue } from "./AddProcessForm";
import { extendErrors, mandatoryValueValidator } from "./graph/node-modal/editors/Validators";
import { useNavigate } from "react-router-dom";
import { NodeValidationError } from "../types";
import { flow, isEmpty } from "lodash";
import { useProcessFormDataOptions } from "./useProcessFormDataOptions";
import { LoadingButtonTypes } from "../windowManager/LoadingButton";

interface AddProcessDialogProps extends WindowContentProps {
    isFragment?: boolean;
    errors: NodeValidationError[];
}

export function AddProcessDialog(props: AddProcessDialogProps): JSX.Element {
    const { t } = useTranslation();
    const { isFragment = false, errors = [], ...passProps } = props;
    const nameValidators = useProcessNameValidators();
    const [value, setState] = useState({ processName: "", processCategory: "", processingMode: "", processEngine: "" });
    const [processNameFromBackend, setProcessNameFromBackendError] = useState<NodeValidationError[]>([]);
    const [engineSetupErrors, setEngineSetupErrors] = useState<Record<string, string[]>>({});
    const [allCombinations, setAllCombinations] = useState<ScenarioParametersCombination[]>([]);
    const engineErrors: NodeValidationError[] = (engineSetupErrors[value.processEngine] ?? []).map((error) => ({
        fieldName: "processEngine",
        errorType: "SaveNotAllowed",
        message: error,
        description: "",
        typ: "",
    }));
    const { categories, engines, processingModes, isEngineFieldVisible, isCategoryFieldVisible } = useProcessFormDataOptions({
        allCombinations,
        value,
    });

    const [isFormSubmitted, setIsFormSubmitted] = useState<boolean>(false);
    const validationErrors = flow(
        (errors) => extendErrors(errors, value.processCategory, "processCategory", isCategoryFieldVisible ? [mandatoryValueValidator] : []),
        (errors) => extendErrors(errors, value.processingMode, "processingMode", [mandatoryValueValidator]),
        (errors) => extendErrors(errors, value.processEngine, "processEngine", isEngineFieldVisible ? [mandatoryValueValidator] : []),
        (errors) => extendErrors([...errors, ...processNameFromBackend, ...engineErrors], value.processName, "processName", nameValidators),
    )(errors);

    const navigate = useNavigate();
    const createProcess = useCallback(async () => {
        if (isEmpty(validationErrors)) {
            const { processName, processCategory, processingMode, processEngine } = value;
            try {
                await HttpService.createProcess({
                    name: processName,
                    category: processCategory || undefined,
                    isFragment,
                    processingMode,
                    engineSetupName: processEngine || undefined,
                });
                passProps.close();
                navigate(visualizationUrl(processName));
            } catch (error) {
                if (error?.response?.status == 400) {
                    setProcessNameFromBackendError(() => [
                        { message: error?.response?.data, description: "", errorType: "SaveAllowed", fieldName: "processName", typ: "" },
                    ]);
                } else {
                    throw error;
                }
            }
        }
    }, [isFragment, validationErrors, navigate, passProps, value]);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => passProps.close(), classname: LoadingButtonTypes.secondaryButton },
            {
                title: t("dialog.button.create", "create"),
                action: async () => {
                    if (!isEmpty(validationErrors) && !isFormSubmitted) {
                        setIsFormSubmitted(true);
                        return;
                    }
                    createProcess();
                },
                disabled: !isEmpty(validationErrors) && isFormSubmitted,
            },
        ],
        [createProcess, isFormSubmitted, passProps, t, validationErrors],
    );

    const onChange = (value: FormValue) => {
        setState(value);

        if (processNameFromBackend.length > 0) {
            setProcessNameFromBackendError([]);
        }
    };

    useEffect(() => {
        HttpService.fetchScenarioParametersCombinations().then((response) => {
            setAllCombinations(response.data.combinations);
            setEngineSetupErrors(response.data.engineSetupErrors);
        });
    }, []);
    return (
        <WindowContent buttons={buttons} {...passProps}>
            <AddProcessForm
                value={value}
                onChange={onChange}
                validationErrors={isFormSubmitted ? validationErrors : []}
                categories={isCategoryFieldVisible ? categories : []}
                processingModes={processingModes}
                engines={isEngineFieldVisible ? engines : []}
            />
        </WindowContent>
    );
}

export default AddProcessDialog;
