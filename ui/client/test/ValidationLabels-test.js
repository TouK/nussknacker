import React from 'react'
import {
  errorValidator,
  HandledErrorType,
  mandatoryValueValidator
} from "../components/graph/node-modal/editors/Validators"
import ValidationLabels from "../components/modals/ValidationLabels"
import {render} from '@testing-library/react'
import {getAllByText, queryAllByText} from "@testing-library/dom"
import {I18nextProvider} from "react-i18next"
import i18n from "../i18n"

describe("test validation labels", () => {
  const fieldName = "fieldName"
  const backendErrorMessage = "backend error message"
  const backendError = (errorType) => ({
    message: backendErrorMessage,
    description: "backend error description",
    typ: errorType,
    fieldName: fieldName
  })

  const testCases = [
    {
      description: "display only fe validation label when both be and fe validators available for the same error type",
      errorType: HandledErrorType.EmptyMandatoryParameter.toString(),
      expectedBackendValidationLabels: 0,
    },
    {
      description: "display both validation labels for different error type",
      errorType: HandledErrorType.WrongDateFormat.toString(),
      expectedBackendValidationLabels: 1,
    },
  ]

  testCases.forEach(({description, errorType, expectedBackendValidationLabels}) => {
    it(description, () => {
      //given
      const validators = [
        mandatoryValueValidator,
        errorValidator([backendError(errorType)], fieldName),
      ]

      //when
      render(
        <I18nextProvider i18n={i18n}>
          <ValidationLabels validators={validators} values={[""]}/>
        </I18nextProvider>
      )

      //then
      const container = document.body
      expect(getAllByText(container, mandatoryValueValidator.message()).length).toBe(1)
      const backendValidationLabels = queryAllByText(container, backendErrorMessage)
      expect((backendValidationLabels === null ? [] : backendValidationLabels).length).toBe(expectedBackendValidationLabels)
    })
  })
})
