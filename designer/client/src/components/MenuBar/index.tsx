import { styled } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";

import Nussknacker from "../../assets/img/nussknacker-logo.svg";
import { useUserSettings } from "../../common/useUserSettings";
import { EnvironmentTag } from "../../containers/EnvironmentTag";
import { RootPath } from "../../containers/paths";
import ProcessBackButton from "../Process/ProcessBackButton";
import { Breadcrumbs } from "./breadcrumbs";
import { HideIfEmbedded } from "./HideIfEmbedded";
import { InstanceLogo } from "./instanceLogo";
import { Menu } from "./menu";

const Header = styled("header")(({ theme }) => ({
    display: "flex",
    overflow: "hidden",
    userSelect: "none",
    background: theme.palette.background.paper,
    height: "3.125em",
    alignItems: "stretch",
}));

const Grid = styled("div")({
    display: "grid",
    gridAutoFlow: "column",
    alignItems: "center",
    columnGap: "1.25em",
    padding: "0 1.25em",
});

const Link = styled(NavLink)({
    display: "flex",
    "&, &:hover, &:focus": {
        color: "inherit",
        textDecoration: "none",
    },
});

const Logo = styled(Nussknacker)({
    height: "1.125em",
});

export function MenuBar(): React.JSX.Element {
    const [showBreadcrumbs] = useUserSettings("scenario.showBreadcrumbs");

    const { t } = useTranslation();

    return (
        <HideIfEmbedded>
            <Header>
                <Grid>
                    <ProcessBackButton />
                    <Link to={RootPath} title={t("menu.goToMainPage", "Go to main page")}>
                        <Logo />
                    </Link>
                    <InstanceLogo />
                    {showBreadcrumbs ? <Breadcrumbs /> : <EnvironmentTag />}
                </Grid>
                <Menu />
            </Header>
        </HideIfEmbedded>
    );
}
