import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { visualizationUrl } from "../common/VisualizationUrl";
import { useProcessNameValidators } from "../containers/hooks/useProcessNameValidators";
import HttpService from "../http/HttpService";
import { WindowContent } from "../windowManager";
import { AddProcessForm } from "./AddProcessForm";
import { allValid, extendErrors, getValidationErrorForField } from "./graph/node-modal/editors/Validators";
import { useNavigate } from "react-router-dom";
import { NodeValidationError } from "../types";
import { FormatterType } from "./graph/node-modal/editors/expression/Formatter";

interface AddProcessDialogProps extends WindowContentProps {
    isFragment?: boolean;
    errors: NodeValidationError[];
}

export function AddProcessDialog(props: AddProcessDialogProps): JSX.Element {
    const { t } = useTranslation();
    const { isFragment, errors = [], ...passProps } = props;
    const nameValidators = useProcessNameValidators();

    const [value, setState] = useState({ processId: "", processCategory: "" });

    const isValid = useMemo(() => value.processCategory && allValid(nameValidators, [value.processId]), [nameValidators, value]);

    const navigate = useNavigate();
    const createProcess = useCallback(async () => {
        if (isValid) {
            const { processId, processCategory } = value;
            await HttpService.createProcess(processId, processCategory, isFragment);
            passProps.close();
            navigate(visualizationUrl(processId));
        }
    }, [isFragment, isValid, navigate, passProps, value]);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => passProps.close() },
            { title: t("dialog.button.create", "create"), action: () => createProcess(), disabled: !isValid },
        ],
        [createProcess, isValid, passProps, t],
    );

    const nameErrors: NodeValidationError[] = nameValidators
        .filter((nameValidator) => !nameValidator.isValid(value))
        .map((nameValidator) => ({
            errorType: "SaveAllowed",
            fieldName: "processName",
            message: nameValidator.message(),
            description: nameValidator.description(),
            typ: FormatterType.String,
        }));

    nameErrors.forEach((nameError) => errors.push(nameError));

    return (
        <WindowContent buttons={buttons} {...passProps}>
            <AddProcessForm
                value={value}
                onChange={setState}
                fieldError={getValidationErrorForField(extendErrors(errors, value.processId, "processName", nameValidators), "processName")}
            />
        </WindowContent>
    );
}

export default AddProcessDialog;
