#!/usr/bin/env bash
curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "/root/awscli-bundle.zip"
unzip /root/awscli-bundle.zip -d /root/
/root/awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws

echo "aws s3 cp s3://$SECRETS_BUCKET/secrets.edn /root/.secrets.edn --region $AWS_REGION"
aws s3 cp s3://$SECRETS_BUCKET/secrets.edn /root/.secrets.edn --region $AWS_REGION
echo "aws s3 cp s3://$SECRETS_BUCKET/prod_pubkey.edn /root/prod_pubkey.edn --region $AWS_REGION"
aws s3 cp s3://$SECRETS_BUCKET/prod_pubkey.edn /root/prod_pubkey.edn --region $AWS_REGION
echo "aws s3 cp s3://$SECRETS_BUCKET/prod_pubkey.edn /root/prod_pubkey.edn --region $AWS_REGION"
aws s3 cp s3://$SECRETS_BUCKET/prod_privkey.edn /root/prod_privkey.edn --region $AWS_REGION
