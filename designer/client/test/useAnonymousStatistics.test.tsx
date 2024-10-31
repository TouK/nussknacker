import { renderHook, waitFor } from "@testing-library/react";
import { useSelector } from "react-redux";
import { jest } from "@jest/globals";
import { useAnonymousStatistics } from "../src/containers/useAnonymousStatistics";
import httpService from "../src/http/HttpService";
import { AxiosResponse } from "axios";

jest.mock("react-redux");
jest.mock("../src/http/HttpService");
jest.mock("rooks", () => ({
    useLocalstorageState: () => {
        return [{ createdAt: Date.now() }, () => {}];
    },
}));

const mockFetchStatisticUrls = httpService.fetchStatisticUsage as jest.MockedFunction<typeof httpService.fetchStatisticUsage>;
const mockUserSelector = useSelector as jest.MockedFunction<typeof useSelector>;

describe("useAnonymousStatistics", () => {
    beforeEach(() => {
        mockUserSelector.mockClear();
        mockFetchStatisticUrls.mockClear();
    });

    it("should fetch the statistics URLs and append them to the document body", async () => {
        const mockUrls = ["http://localhost/url1", "http://localhost/url2"];
        const mockResponse = { data: { urls: mockUrls } } as AxiosResponse;
        mockFetchStatisticUrls.mockResolvedValue(mockResponse);

        mockUserSelector.mockReturnValue({ usageStatisticsReports: { enabled: true } });

        renderHook(() => useAnonymousStatistics(0));

        expect(httpService.fetchStatisticUsage).toHaveBeenCalledTimes(1);
        expect(httpService.fetchStatisticUsage).toHaveBeenCalledWith();

        // Verify first anonymous statistic URL
        await waitFor(() => {
            const usageStatisticsImg = document.getElementById("usage-statistics") as HTMLImageElement;

            expect(usageStatisticsImg.src).toEqual(mockUrls[0]);
            expect(usageStatisticsImg.alt).toEqual("anonymous usage reporting");
        });

        // Verify second anonymous statistic URL
        await waitFor(() => {
            const usageStatisticsImg = document.getElementById("usage-statistics") as HTMLImageElement;

            expect(usageStatisticsImg?.src).toEqual(mockUrls[1]);
            expect(usageStatisticsImg.alt).toEqual("anonymous usage reporting");
        });
    });

    it("should not fetch the statistics URLs when the feature is disabled", async () => {
        mockUserSelector.mockReturnValueOnce({ usageStatisticsReports: { enabled: false } });

        renderHook(() => useAnonymousStatistics(1));

        // Ensure that fetchStatisticUrls is not called
        expect(httpService.fetchStatisticUsage).not.toHaveBeenCalled();
    });

    it("should fetch the statistics URLs when the cache becomes expired", async () => {
        const mockUrls = ["http://localhost/url1", "http://localhost/url2"];
        const mockResponse = { data: { urls: mockUrls } } as AxiosResponse;
        mockFetchStatisticUrls.mockResolvedValue(mockResponse);

        mockUserSelector.mockReturnValue({ usageStatisticsReports: { enabled: true } });

        renderHook(() => useAnonymousStatistics(0.001));

        await waitFor(() => {
            expect(httpService.fetchStatisticUsage).toHaveBeenCalledTimes(1);
        });
    });

    it("should not fetch the statistics URLs when the cache is still valid", async () => {
        mockUserSelector.mockReturnValue({ usageStatisticsReports: { enabled: true } });

        renderHook(() => useAnonymousStatistics(1));

        expect(httpService.fetchStatisticUsage).toHaveBeenCalledTimes(0);
    });
});
