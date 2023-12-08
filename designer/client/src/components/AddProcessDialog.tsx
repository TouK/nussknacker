import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { visualizationUrl } from "../common/VisualizationUrl";
import { useProcessNameValidators } from "../containers/hooks/useProcessNameValidators";
import HttpService from "../http/HttpService";
import { WindowContent } from "../windowManager";
import { AddProcessForm } from "./AddProcessForm";
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

    const [value, setState] = useState({ processId: "", processCategory: "" });

    const fieldErrors = getValidationErrorsForField(extendErrors(errors, value.processId, "processName", nameValidators), "processName");

    const navigate = useNavigate();
    const createProcess = useCallback(async () => {
        if (isEmpty(fieldErrors)) {
            const { processId, processCategory } = value;
            await HttpService.createProcess(processId, processCategory, isFragment);
            passProps.close();
            navigate(visualizationUrl(processId));
        }
    }, [isFragment, fieldErrors, navigate, passProps, value]);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => passProps.close() },
            { title: t("dialog.button.create", "create"), action: () => createProcess(), disabled: Boolean(fieldErrors) },
        ],
        [createProcess, fieldErrors, passProps, t],
    );

    return (
        <WindowContent buttons={buttons} {...passProps}>
            <AddProcessForm value={value} onChange={setState} fieldErrors={fieldErrors} />
        </WindowContent>
    );
}

export default AddProcessDialog;
