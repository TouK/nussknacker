import moment from "moment";
import React from "react";
import { CountsRangesButtons } from "../src/components/modals/CalculateCounts/CountsRangesButtons";
import { fireEvent, render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { NuThemeProvider } from "../src/containers/theme/nuThemeProvider";

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: { changeLanguage: () => {} },
    }),
}));

describe("CountsRangesButtons tests", () => {
    const m = moment("2001-10-19T23:00:00.000Z");
    const range1 = { name: "range1", from: () => m.clone(), to: () => m.clone().add(1, "hour") };
    const range2 = { name: "range2", from: () => m.clone().add(1, "day"), to: () => m.clone().add(2, "days") };
    const range3 = { name: "range3", from: () => m.clone().add(1, "week"), to: () => m.clone().add(2, "weeks") };
    const ranges = [range1, range2, range3];
    const changeFn = jest.fn();

    beforeEach(() => {
        changeFn.mockReset();
    });

    it("should render buttons", () => {
        const { container } = render(
            <NuThemeProvider>
                <CountsRangesButtons ranges={ranges} onChange={changeFn} limit={2} />
            </NuThemeProvider>,
        );
        expect(container).toMatchSnapshot();
    });

    it("should handle click", () => {
        render(
            <NuThemeProvider>
                <CountsRangesButtons ranges={ranges} onChange={changeFn} />
            </NuThemeProvider>,
        );
        fireEvent.click(screen.getByRole("button", { name: /range1/ }));
        expect(changeFn).toHaveBeenCalledTimes(1);
        expect(changeFn).toHaveBeenCalledWith([range1.from(), range1.to()], null);
    });

    it("should collapse buttons", () => {
        render(
            <div>
                <NuThemeProvider>
                    <CountsRangesButtons ranges={ranges} onChange={changeFn} limit={1} />
                </NuThemeProvider>
            </div>,
        );

        const buttons = screen.getAllByRole("button");
        expect(buttons).toHaveLength(2);
        const options = document.getElementsByClassName("nodeValueSelect__option");
        expect(options).toHaveLength(0);
        fireEvent.click(buttons[buttons.length - 1]);
        const options2 = document.getElementsByClassName("nodeValueSelect__option");
        expect(options2).toHaveLength(2);
        fireEvent.click(options2.item(1));
        expect(changeFn).toHaveBeenCalledTimes(1);
        expect(changeFn).toHaveBeenCalledWith([range3.from(), range3.to()], null);
    });

    it("should hide expand button when not needed", () => {
        render(
            <NuThemeProvider>
                <CountsRangesButtons ranges={ranges} onChange={changeFn} limit={10} />
            </NuThemeProvider>,
        );

        const buttons = screen.getAllByRole("button");
        expect(buttons).toHaveLength(3);
        fireEvent.click(buttons[buttons.length - 1]);
        expect(changeFn).toHaveBeenCalledTimes(1);
        expect(changeFn).toHaveBeenCalledWith([range3.from(), range3.to()], null);
    });
});
