#!/bin/bash -e

cd "$(dirname "$0")"

source /app/utils/lib.sh

ID=$(random_Ndigit_number 10)
EVENT_TYPE="$(pick_randomly "ClientCloseToShowroom" "ClientBrowseOffers" "ClientEndedCallWithCustomerService" "ClientSentTerminationLetter" "Other")"

echo "{ \"customerId\": \"$ID\", \"eventType\": \"$EVENT_TYPE\" }"