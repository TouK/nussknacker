import React from "react";
import { ErrorHeader } from "./ErrorHeader";
import i18next from "i18next";
import { ErrorLinkStyle } from "./styled";
import { NavLink } from "react-router-dom";
import { useOpenProperties } from "../../toolbars/scenarioActions/buttons/PropertiesButton";
import { useTranslation } from "react-i18next";

export const ScenarioPropertiesSection = () => {
    const { t } = useTranslation();
    const openProperties = useOpenProperties();
    return (
        <>
            <ErrorHeader message={i18next.t("errors.errorsIn", "Errors in: ")} />
            <ErrorLinkStyle variant={"body2"} component={NavLink} onClick={openProperties}>
                {t("errors.propertiesText", "properties")}
            </ErrorLinkStyle>
        </>
    );
};
