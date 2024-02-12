import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { visualizationUrl } from "../common/VisualizationUrl";
import { useProcessNameValidators } from "../containers/hooks/useProcessNameValidators";
import HttpService from "../http/HttpService";
import { WindowContent } from "../windowManager";
import { AddProcessForm, FormValue } from "./AddProcessForm";
import { extendErrors, getValidationErrorsForField } from "./graph/node-modal/editors/Validators";
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
    const [value, setState] = useState({ processName: "", processCategory: "", processingMode: "" });
    const [processNameFromBackend, setProcessNameFromBackendError] = useState<NodeValidationError[]>([]);

    const fieldErrors = getValidationErrorsForField(
        extendErrors([...errors, ...processNameFromBackend], value.processName, "processName", nameValidators),
        "processName",
    );

    const navigate = useNavigate();
    const createProcess = useCallback(async () => {
        if (isEmpty(fieldErrors)) {
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
    }, [isFragment, fieldErrors, navigate, passProps, value]);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => passProps.close() },
            { title: t("dialog.button.create", "create"), action: () => createProcess(), disabled: !isEmpty(fieldErrors) },
        ],
        [createProcess, fieldErrors, passProps, t],
    );

    const onChange = (value: FormValue) => {
        setState(value);

        if (processNameFromBackend.length > 0) {
            setProcessNameFromBackendError([]);
        }
    };
    return (
        <WindowContent buttons={buttons} {...passProps}>
            <AddProcessForm value={value} onChange={onChange} fieldErrors={fieldErrors} />
        </WindowContent>
    );
}

export default AddProcessDialog;
