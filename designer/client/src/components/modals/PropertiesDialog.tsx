import { css } from "@emotion/css";
import { styled } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { debounce, isEqual } from "lodash";
import { set } from "lodash/fp";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { editProperties } from "../../actions/nk";
import PropertiesSvg from "../../assets/img/properties.svg";
import HttpService from "../../http/HttpService";
import type { RootState } from "../../reducers";
import { getProperties, getScenario } from "../../reducers/selectors/graph";
import type { NodeValidationError, PropertiesType } from "../../types";
import { WindowContent, WindowKind } from "../../windowManager";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { ContentSize } from "../graph/node-modal/node/ContentSize";
import { getPropertiesErrors, getReadOnly } from "../graph/node-modal/node/selectors";
import { WindowHeaderIconStyled } from "../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../graph/node-modal/nodeDetails/SubHeader";
import { getProcessName, getScenarioPropertiesConfig } from "../graph/node-modal/NodeDetailsContent/selectors";
import { PropertiesForm } from "../properties";

export const usePropertiesState = () => {
    const currentProperties = useSelector(getProperties);
    const [editedProperties, setEditedProperties] = useState<PropertiesType>(currentProperties);
    const isTouched = useMemo(() => !isEqual(currentProperties, editedProperties), [currentProperties, editedProperties]);

    const handleSetEditedProperties = useCallback((label: string | number, value: string) => {
        setEditedProperties((prevState) => set<typeof editedProperties>(label, value, prevState) as unknown as typeof editedProperties);
    }, []);

    return { currentProperties, editedProperties, handleSetEditedProperties, isTouched };
};

export const NodeDetailsModalIcon = styled(WindowHeaderIconStyled.withComponent(PropertiesSvg))(({ theme }) => ({
    backgroundColor: theme.palette.custom.getWindowStyles(WindowKind.editProperties).backgroundColor,
}));

const PropertiesDialog = ({ ...props }: WindowContentProps) => {
    const isEditMode = !useSelector((s: RootState) => getReadOnly(s, false));

    const { t } = useTranslation();
    const dispatch = useDispatch();

    const globalPropertiesErrors = useSelector(getPropertiesErrors);
    const scenarioProperties = useSelector(getScenarioPropertiesConfig);
    const scenario = useSelector(getScenario);
    const scenarioName = useSelector(getProcessName);

    const [errors, setErrors] = useState<NodeValidationError[]>(isEditMode ? globalPropertiesErrors : []);
    const { editedProperties, handleSetEditedProperties } = usePropertiesState();
    const showSwitch = false;

    const debouncedValidateProperties = useMemo(() => {
        return debounce((scenarioName, additionalFields, id) => {
            HttpService.validateProperties(scenarioName, { additionalFields: additionalFields, name: id }).then((data) => {
                if (data) {
                    setErrors(data.validationErrors);
                }
            });
        }, 500);
    }, []);

    const apply = useMemo<WindowButtonProps>(() => {
        return {
            title: t("dialog.button.apply", "apply"),
            action: async () => {
                await dispatch(editProperties(scenario, editedProperties));
                props.close();
            },
        };
    }, [dispatch, editedProperties, props, scenario, t]);

    const cancel = useMemo<WindowButtonProps | false>(() => {
        return {
            title: t("dialog.button.cancel", "cancel"),
            action: () => props.close(),
            className: LoadingButtonTypes.secondaryButton,
        };
    }, [props, t]);

    useEffect(() => {
        if (!isEditMode) {
            return;
        }

        debouncedValidateProperties(scenarioName, editedProperties.additionalFields, editedProperties.name);
    }, [debouncedValidateProperties, isEditMode, editedProperties.additionalFields, editedProperties.name, scenarioName]);

    return (
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
                    />
                </ContentSize>
            </div>
        </WindowContent>
    );
};

export default PropertiesDialog;
