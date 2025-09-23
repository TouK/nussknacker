import type { PropsOf } from "@emotion/react";

import type PasswordMask from "./PasswordMask";
import type RouterLink from "./RouterLink";

// might look like typo, but it's on purpose - makes it more unique and less likely to run by accident.
export const SANITIZED_PASSWORD_TAG_NAME = "sanitizd-passwrd" as const;
export const ROUTER_LINK_TAG_NAME = "router-link" as const;

declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace JSX {
        interface IntrinsicElements {
            [SANITIZED_PASSWORD_TAG_NAME]: PropsOf<typeof PasswordMask>;
            [ROUTER_LINK_TAG_NAME]: PropsOf<typeof RouterLink>;
        }
    }
}
