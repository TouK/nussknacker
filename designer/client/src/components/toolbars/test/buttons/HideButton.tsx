import React from "react";
import { useTranslation } from "react-i18next";
import { RootState } from "../../../../reducers";
import { connect } from "react-redux";
import { hideRunProcessDetails } from "../../../../actions/nk";
import { getShowRunProcessDetails } from "../../../../reducers/selectors/graph";
import Icon from "../../../../assets/img/toolbarButtons/hide.svg";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";

type Props = StateProps & ToolbarButtonProps;

function HideButton(props: Props) {
    const { showRunProcessDetails, hideRunProcessDetails, disabled, type } = props;
    const available = !disabled && showRunProcessDetails;
    const { t } = useTranslation();

    return (
        <ToolbarButton
            name={t("panels.actions.test-hide.button.name", "hide")}
            title={t("panels.actions.test-hide.button.title", "hide counts")}
            icon={<Icon />}
            disabled={!available}
            onClick={() => hideRunProcessDetails()}
            type={type}
        />
    );
}

const mapState = (state: RootState) => ({
    showRunProcessDetails: getShowRunProcessDetails(state),
});

const mapDispatch = {
    hideRunProcessDetails,
};

export type StateProps = typeof mapDispatch & ReturnType<typeof mapState>;

export default connect(mapState, mapDispatch)(HideButton);
