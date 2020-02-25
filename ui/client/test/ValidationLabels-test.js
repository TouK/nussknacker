import React from 'react'
import {
  errorValidator,
  HandledErrorType,
  mandatoryValueValidator
} from "../components/graph/node-modal/editors/Validators"
import ValidationLabels from "../components/modals/ValidationLabels"
import {render, screen} from '@testing-library/react'

describe("test validation labels", () => {
  const backendErrorDescription = "test"
  const backendError = (errorType) => ({
    message: "Test",
    description: backendErrorDescription,
    typ: errorType,
    fieldName: "fieldName"
  })

  const testCases = [
    {
      description: "display only fe validation label when both be and fe validators available for the same error type",
      errorType: HandledErrorType.EmptyMandatoryParameter.toString(),
      expectedBackendValidationLabels: 0,
    },
    {
      description: "display both validation labels for different error type",
      errorType: "AnotherErrorType",
      expectedBackendValidationLabels: 1,
    },
  ]

  testCases.forEach(({description, errorType, expectedBackendValidationLabels}) => {
    it(description, () => {
      //given
      const validators = [
        mandatoryValueValidator,
        errorValidator([backendError(errorType)], "fieldName"),
      ]

      //when
      render(<ValidationLabels validators={validators} values={[""]}/>)

      //then
      expect(screen.findByDisplayValue(mandatoryValueValidator.description).length).toBe(1)
      expect(screen.findAllByDisplayValue(backendErrorDescription).length).toBe(expectedBackendValidationLabels)
    })
  })
})
