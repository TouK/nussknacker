import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { visualizationUrl } from "../common/VisualizationUrl";
import { useProcessNameValidators } from "../containers/hooks/useProcessNameValidators";
import HttpService, { ProcessingMode } from "../http/HttpService";
import { WindowContent } from "../windowManager";
import { AddProcessForm, FormValue, TouchedValue } from "./AddProcessForm";
import { extendErrors, mandatoryValueValidator } from "./graph/node-modal/editors/Validators";
import { useNavigate } from "react-router-dom";
import { NodeValidationError } from "../types";
import { flow, isEmpty, transform } from "lodash";
import { useProcessFormDataOptions } from "./useProcessFormDataOptions";
import { LoadingButtonTypes } from "../windowManager/LoadingButton";
import { useGetAllCombinations } from "./useGetAllCombinations";

interface AddProcessDialogProps extends WindowContentProps {
    isFragment?: boolean;
    errors?: NodeValidationError[];
}

export function AddProcessDialog(props: AddProcessDialogProps): JSX.Element {
    const { t } = useTranslation();
    const { isFragment = false, errors = [], ...passProps } = props;
    const nameValidators = useProcessNameValidators();
    const [value, setState] = useState<FormValue>({
        processName: "",
        processCategory: "",
        processingMode: "" as ProcessingMode,
        processEngine: "",
    });
    const [touched, setTouched] = useState<TouchedValue>({
        processName: false,
        processCategory: false,
        processingMode: false,
        processEngine: false,
    });
    const [processNameFromBackend, setProcessNameFromBackendError] = useState<NodeValidationError[]>([]);

    const { engineSetupErrors, allCombinations } = useGetAllCombinations({
        processCategory: value.processCategory,
        processingMode: value.processingMode,
        processEngine: value.processEngine,
    });

    const engineErrors: NodeValidationError[] = (engineSetupErrors[value.processEngine] ?? []).map((error) => ({
        fieldName: "processEngine",
        errorType: "SaveNotAllowed",
        message: error,
        description: "",
        typ: "",
    }));
    const { categories, engines, processingModes, isEngineFieldVisible, isCategoryFieldVisible, isProcessingModeBatchAvailable } =
        useProcessFormDataOptions({
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

    const setAllFieldTouched = useCallback(() => {
        setTouched((prevState) =>
            transform(prevState, (currentObjet, _, key) => {
                currentObjet[key.trim()] = true;
            }),
        );
    }, []);

    const displayContactSupportMessage = useMemo(
        () => !isProcessingModeBatchAvailable && value.processingMode === ProcessingMode.batch,
        [isProcessingModeBatchAvailable, value.processingMode],
    );
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => passProps.close(), classname: LoadingButtonTypes.secondaryButton },
            {
                title: t("dialog.button.create", "create"),
                action: async () => {
                    if (!isEmpty(validationErrors)) {
                        setAllFieldTouched();
                        setIsFormSubmitted(true);
                        return;
                    }
                    createProcess();
                },
                disabled: (!isEmpty(validationErrors) && isFormSubmitted) || displayContactSupportMessage,
            },
        ],
        [createProcess, displayContactSupportMessage, isFormSubmitted, passProps, setAllFieldTouched, t, validationErrors],
    );

    const onChange = (value: FormValue) => {
        setState(value);

        if (processNameFromBackend.length > 0) {
            setProcessNameFromBackendError([]);
        }
    };

    const handleSetTouched = (touched: TouchedValue) => {
        setTouched(touched);
    };

    return (
        <WindowContent buttons={buttons} {...passProps}>
            <AddProcessForm
                value={value}
                onChange={onChange}
                validationErrors={validationErrors}
                categories={isCategoryFieldVisible ? categories : []}
                processingModes={processingModes}
                engines={isEngineFieldVisible ? engines : []}
                touched={touched}
                handleSetTouched={handleSetTouched}
                displayContactSupportMessage={displayContactSupportMessage}
            />
        </WindowContent>
    );
}

export default AddProcessDialog;
