import React from "react";
import { css } from "@emotion/css";

const styles = css`
    display: flex;
    color: #ffffff;
    background: #cc0000;
    flex-direction: column;
    align-items: center;
    text-align: center;
    box-sizing: border-box;
    justify-content: center;
    height: 100%;
    width: 100%;

    svg {
        --color1: #ff00ff;
        --color2: #00ff00;

        animation-duration: 0.01s;
        animation-name: flicker;
        animation-iteration-count: infinite;
        animation-direction: alternate;

        flex: 1;
        fill: currentColor;
        max-height: 60vh;
        margin: 0 15% 0 15%;
    }

    @keyframes flicker {
        from {
            filter: drop-shadow(1px 0 0 var(--color1)) drop-shadow(-2px 0 0 var(--color2));
        }
        to {
            filter: drop-shadow(2px 0.5px 2px var(--color1)) drop-shadow(-1px -0.5px 2px var(--color2));
        }
    }
`;

export const CrackedNut = () => (
    <div className={styles}>
        <svg viewBox="0 0 500 500" preserveAspectRatio="xMidYMid" xmlns="http://www.w3.org/2000/svg">
            <path
                d="M 112.842 484.323 C 100.404 484.343 88.07 482.11 76.428 477.739 C 65.255 473.572 55.174 466.927 46.929 458.314 C 38.073 448.87 31.337 437.645 27.175 425.39 C 22.145 410.398 19.742 394.646 20.065 378.836 L 20.065 299.226 L 83.409 259.719 L 86.438 259.719 L 86.438 365.798 C 85.49 381.373 90.165 396.767 99.608 409.192 C 108.385 419.55 121.554 424.731 139.115 424.731 L 144.383 424.731 C 161.945 424.731 175.114 419.55 183.892 409.192 C 193.333 396.767 198.002 381.373 197.061 365.798 L 197.061 259.719 L 199.958 259.719 L 263.507 299.226 L 263.507 379.429 C 263.829 395.239 261.428 410.988 256.395 425.982 C 252.207 438.223 245.479 449.444 236.642 458.906 C 228.37 467.532 218.263 474.17 207.069 478.331 C 195.449 482.697 183.135 484.93 170.722 484.916 Z"
                transform="matrix(0.5, 0.866025, -0.866025, 0.5, 393.329349, 63.368501)"
            ></path>
            <path
                d="M 515.096 479.569 L 515.096 446.646 C 515.34 436.985 512.878 427.451 507.985 419.122 C 503.066 411.056 496.296 404.28 488.23 399.367 C 479.064 393.96 469.076 390.091 458.665 387.909 C 446.72 385.336 434.531 384.078 422.317 384.157 L 415.734 384.157 L 415.734 333.059 L 371.153 333.059 L 371.153 384.419 L 364.569 384.419 C 352.329 384.334 340.119 385.592 328.156 388.174 C 317.759 390.339 307.789 394.211 298.657 399.632 C 290.57 404.524 283.794 411.299 278.902 419.384 C 274.01 427.715 271.547 437.249 271.79 446.909 L 271.79 479.833 L 279.099 479.833 L 346.198 437.69 L 389.591 507.158 L 397.031 507.158 L 440.426 437.69 L 507.786 479.569 Z"
                transform="matrix(-0.743145, 0.669131, -0.669131, -0.743145, 966.935735, 469.045029)"
            ></path>
        </svg>
    </div>
);
