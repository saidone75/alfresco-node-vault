#!/bin/bash

set -e

awslocal s3api \
create-bucket --bucket anv \
--create-bucket-configuration LocationConstraint=eu-central-1 \
--region eu-central-1

echo "$(date) - 🎉 Init script executed!"