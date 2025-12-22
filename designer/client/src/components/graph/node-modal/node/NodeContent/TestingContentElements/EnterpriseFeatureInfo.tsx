import { Box, Link, Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useMemo } from "react";
import { Trans, useTranslation } from "react-i18next";

import { LoadingButtonTypes } from "../../../../../../windowManager/LoadingButton";
import { WindowContent } from "../../../../../../windowManager/WindowContent";

const EnterpriseFeatureInfo = (props: WindowContentProps) => {
    const { t } = useTranslation();

    const buttons: WindowButtonProps[] = useMemo(
        () => [{ title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton }],
        [props, t],
    );

    return (
        <WindowContent {...props} buttons={buttons}>
            <Box p={2}>
                <Typography>
                    <Trans i18nKey="displayContactSupportMessage">
                        Multiple test suites are available as part of our enterprise plan. If you’re interested in this feature, just drop
                        us a message at{" "}
                        <Link textAlign={"center"} href="mailto:enterprise@nussknacker.io">
                            enterprise@nussknacker.io
                        </Link>
                    </Trans>
                </Typography>
            </Box>
        </WindowContent>
    );
};

export default EnterpriseFeatureInfo;
