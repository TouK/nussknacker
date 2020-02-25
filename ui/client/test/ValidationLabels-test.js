import '@testing-library/jest-dom/extend-expect'
import React from 'react'
import {errorValidator, mandatoryValueValidator} from "../components/graph/node-modal/editors/Validators"
import ValidationLabels from "../components/modals/ValidationLabels"

describe("test validation labels", () => {
  const backendErrorDescription = "test"
  const fieldName = "fieldName"
  const emptyValue = ""
  const validators = (backendError) => [mandatoryValueValidator, errorValidator([backendError], fieldName)]

  const testCases = [
    {
      description: "display only fe validation label when both be and fe validators available for the same error type",
      error: backendError(mandatoryValueValidator.handledErrorType),
      expectedFeValidationLabels: 1,
      expectedBackendValidationLabels: 0,
    },
    {
      description: "display both validation labels for different error type",
      error: backendError("AnotherErrorType"),
      expectedFeValidationLabels: 1,
      expectedBackendValidationLabels: 1,
    },
  ]

  testCases.forEach((testCase) => {
    it(testCase.description, () => {
      //given
      const validators = [
        mandatoryValueValidator,
        errorValidator([testCase.error], "fieldName"),
      ]

      //when
      render(<ValidationLabels validators={validators} values={[emptyValue]}/>)

      //then
      expect(screen.findAllByText(mandatoryValueValidator.description).length).toBe(testCase.expectedFeValidationLabels)
      expect(screen.findAllByText(backendErrorDescription).length).toBe(testCase.expectedBackendValidationLabels)
    })
  })

  function backendError(errorType) {
    return {
      message: "Test",
      description: backendErrorDescription,
      typ: errorType,
      fieldName: "fieldName"
    }
  }
})
