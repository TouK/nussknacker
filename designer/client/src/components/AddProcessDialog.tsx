import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { visualizationUrl } from "../common/VisualizationUrl";
import { useProcessNameValidators } from "../containers/hooks/useProcessNameValidators";
import HttpService, { ScenarioParametersCombination } from "../http/HttpService";
import { WindowContent } from "../windowManager";
import { AddProcessForm, FormValue } from "./AddProcessForm";
import { extendErrors } from "./graph/node-modal/editors/Validators";
import { useNavigate } from "react-router-dom";
import { NodeValidationError } from "../types";
import { isEmpty } from "lodash";

interface AddProcessDialogProps extends WindowContentProps {
    isFragment?: boolean;
    errors: NodeValidationError[];
}

export function AddProcessDialog(props: AddProcessDialogProps): JSX.Element {
    const { t } = useTranslation();
    const { isFragment, errors = [], ...passProps } = props;
    const nameValidators = useProcessNameValidators();
    const [value, setState] = useState({ processName: "", processCategory: "", processingMode: "", processEngine: "" });
    const [processNameFromBackend, setProcessNameFromBackendError] = useState<NodeValidationError[]>([]);
    const [engineSetupErrors, setEngineSetupErrors] = useState<Record<string, string[]>>({});
    const [allCombinations, setAllCombinations] = useState<ScenarioParametersCombination[]>([]);
    const engineErrors: NodeValidationError[] =
        engineSetupErrors[value.processEngine]?.map((error) => ({
            fieldName: "processEngine",
            errorType: "SaveNotAllowed",
            message: error,
            description: "",
            typ: "",
        })) || [];

    const validationErrors = extendErrors(
        [...errors, ...processNameFromBackend, ...engineErrors],
        value.processName,
        "processName",
        nameValidators,
    );

    const navigate = useNavigate();
    const createProcess = useCallback(async () => {
        if (isEmpty(validationErrors)) {
            const { processName, processCategory } = value;
            try {
                await HttpService.createProcess(processName, processCategory, isFragment);
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
            { title: t("dialog.button.cancel", "Cancel"), action: () => passProps.close() },
            { title: t("dialog.button.create", "create"), action: () => createProcess(), disabled: !isEmpty(validationErrors) },
        ],
        [createProcess, validationErrors, passProps, t],
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
            <AddProcessForm value={value} onChange={onChange} validationErrors={validationErrors} allCombinations={allCombinations} />
        </WindowContent>
    );
}

export default AddProcessDialog;
