import { css } from "@emotion/css";
import { styled } from "@mui/material";
import type { WindowContentProps } from "@touk/window-manager";
import type { DefaultContentProps } from "@touk/window-manager/cjs/components/window/DefaultContent";
import { debounce } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";

import { ToolId } from "../../actions/nk/toolWindow";
import PropertiesSvg from "../../assets/img/properties.svg";
import HttpService from "../../http/HttpService/instance";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useAppSelector } from "../../store/storeHelpers";
import type { PropertiesType } from "../../types/node";
import type { NodeValidationError } from "../../types/validation";
import { WindowContent } from "../../windowManager/WindowContent";
import { WindowKind } from "../../windowManager/WindowKind";
import { CloseButtonWithEditLock } from "../graph/node-modal/node/CloseButtonWithEditLock";
import { ContentSize } from "../graph/node-modal/node/ContentSize";
import { EditStateFeedback } from "../graph/node-modal/node/EditStateFeedback";
import { getPropertiesErrors, getReadOnly } from "../graph/node-modal/node/selectors";
import { useDialogActions } from "../graph/node-modal/node/useDialogActions";
import { WindowHeaderIconStyled } from "../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../graph/node-modal/nodeDetails/SubHeader";
import { getProcessName, getScenarioProperties } from "../graph/node-modal/NodeDetailsContent/selectors";
import { PropertiesForm } from "../properties/PropertiesForm";
import { useOnToolWindow } from "./useOnToolWindow";
import { usePropertiesState } from "./usePropertiesState";

export const NodeDetailsModalIcon = styled(WindowHeaderIconStyled.withComponent(PropertiesSvg))(({ theme }) => ({
    backgroundColor: theme.palette.custom.getWindowStyles(WindowKind.editProperties).backgroundColor,
}));

function usePropertiesValidation(isEditMode: boolean, editedProperties: PropertiesType) {
    const scenarioName = useAppSelector(getProcessName);
    const globalPropertiesErrors = useAppSelector(getPropertiesErrors);
    const [errors, setErrors] = useState<NodeValidationError[]>(isEditMode ? globalPropertiesErrors : []);

    const debouncedValidateProperties = useMemo(() => {
        return debounce((scenarioName, additionalFields, id) => {
            HttpService.validateProperties(scenarioName, {
                additionalFields: additionalFields,
                name: id,
            }).then((data) => {
                if (data) {
                    setErrors(data.validationErrors);
                }
            });
        }, 500);
    }, []);

    useEffect(() => {
        if (!isEditMode) {
            return;
        }

        debouncedValidateProperties(scenarioName, editedProperties.additionalFields, editedProperties.name);
    }, [debouncedValidateProperties, isEditMode, editedProperties.additionalFields, editedProperties.name, scenarioName]);

    return errors;
}

const PropertiesDialog = ({ ...props }: WindowContentProps) => {
    const isEditMode = useAppSelector((s) => !getReadOnly(s, false));
    const scenarioProperties = useAppSelector(getScenarioProperties);

    const showSwitch = false;
    const { editedProperties, handleSetEditedProperties, manualApply, editState, editStateRef } = usePropertiesState();

    const { apply, cancel } = useDialogActions({
        onApply: manualApply,
        onClose: props.close,
        readOnly: !isEditMode,
    });

    const errors = usePropertiesValidation(isEditMode, editedProperties);

    const HeaderButtonClose: DefaultContentProps["components"]["HeaderButtonClose"] = useCallback(
        (props) => {
            return <CloseButtonWithEditLock {...props} editStateRef={editStateRef} />;
        },
        [editStateRef],
    );

    const components: DefaultContentProps["components"] = useMemo(() => {
        return { HeaderButtonClose };
    }, [HeaderButtonClose]);

    const settings = useAppSelector(getUserSettings);

    useOnToolWindow(ToolId.properties);

    return (
        <>
            {settings["node.autoApply"] ? <EditStateFeedback editState={editState} /> : null}
            <WindowContent
                {...props}
                closeWithEsc={editState === "idle"}
                buttons={[cancel, apply]}
                title={"Properties"}
                icon={<NodeDetailsModalIcon />}
                subheader={<NodeDocs href={scenarioProperties.docsUrl} />}
                classnames={{
                    content: css({ minHeight: "100%", display: "flex", ">div": { flex: 1 }, position: "relative" }),
                }}
                components={components}
            >
                <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
                    <ContentSize>
                        <PropertiesForm
                            editedProperties={editedProperties}
                            handleSetEditedProperties={isEditMode ? handleSetEditedProperties : undefined}
                            errors={errors}
                            showSwitch={showSwitch}
                        />
                    </ContentSize>
                </div>
            </WindowContent>
        </>
    );
};

export default PropertiesDialog;
