import { css } from "@emotion/css";
import { styled } from "@mui/material";
import type { WindowContentProps } from "@touk/window-manager";
import React, { useEffect } from "react";
import { useDebounceFn } from "rooks";

import { ToolId } from "../../actions/nk/toolWindow";
import { validateScenarioProperties } from "../../actions/nk/validationsActions";
import PropertiesSvg from "../../assets/img/properties.svg";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import type { PropertiesType } from "../../types/node";
import { WindowContent } from "../../windowManager/WindowContent";
import { WindowKind } from "../../windowManager/WindowKind";
import { ContentSize } from "../graph/node-modal/node/ContentSize";
import { EditStateFeedback } from "../graph/node-modal/node/EditStateFeedback";
import { getCurrentPropertiesErrors, getIsPropertiesValidating, getReadOnly } from "../graph/node-modal/node/selectors";
import { useDialogActions } from "../graph/node-modal/node/useDialogActions";
import { WindowHeaderIconStyled } from "../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../graph/node-modal/nodeDetails/SubHeader";
import { getScenarioProperties } from "../graph/node-modal/NodeDetailsContent/selectors";
import { PropertiesForm } from "../properties/PropertiesForm";
import { useOnToolWindow } from "./useOnToolWindow";
import { usePropertiesState } from "./usePropertiesState";

export const NodeDetailsModalIcon = styled(WindowHeaderIconStyled.withComponent(PropertiesSvg))(({ theme }) => ({
    backgroundColor: theme.palette.custom.getWindowStyles(WindowKind.editProperties).backgroundColor,
}));

function usePropertiesValidation(isEditMode: boolean, editedProperties: PropertiesType) {
    const dispatch = useAppDispatch();
    const errors = useAppSelector(getCurrentPropertiesErrors);

    const [debouncedValidateProperties] = useDebounceFn((props: PropertiesType) => dispatch(validateScenarioProperties(props)), 500);

    useEffect(() => {
        if (!isEditMode) return;
        debouncedValidateProperties(editedProperties);
    }, [debouncedValidateProperties, editedProperties, isEditMode]);

    return errors;
}

const PropertiesDialog = ({ ...props }: WindowContentProps) => {
    const isEditMode = useAppSelector((s) => !getReadOnly(s, false));
    const scenarioProperties = useAppSelector(getScenarioProperties);

    const showSwitch = false;
    const { editedProperties, handleSetEditedProperties, manualApply, editState } = usePropertiesState();

    const { apply, cancel } = useDialogActions({
        onApply: manualApply,
        onClose: props.close,
        readOnly: !isEditMode,
    });

    const errors = usePropertiesValidation(isEditMode, editedProperties);
    const isValidating = useAppSelector((state) => getIsPropertiesValidating(state));

    useOnToolWindow(ToolId.properties);

    return (
        <>
            <EditStateFeedback editState={editState} />
            <WindowContent
                {...props}
                closeWithEsc
                buttons={[cancel, apply]}
                title={"Properties"}
                icon={<NodeDetailsModalIcon />}
                subheader={<NodeDocs href={scenarioProperties.docsUrl} />}
                classnames={{
                    content: css({ minHeight: "100%", display: "flex", ">div": { flex: 1 }, position: "relative" }),
                }}
            >
                <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
                    <ContentSize>
                        <PropertiesForm
                            editedProperties={editedProperties}
                            handleSetEditedProperties={isEditMode ? handleSetEditedProperties : undefined}
                            errors={errors}
                            showSwitch={showSwitch}
                            isValidating={isValidating}
                        />
                    </ContentSize>
                </div>
            </WindowContent>
        </>
    );
};

export default PropertiesDialog;
