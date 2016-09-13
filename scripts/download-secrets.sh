#!/usr/bin/env bash
curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "/root/awscli-bundle.zip"
unzip /root/awscli-bundle.zip -d /root/
/root/awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws

aws s3 cp s3://kixi-vault/secrets.edn /root/.secrets.edn
aws s3 cp s3://kixi-vault/prod_pubkey.edn /root/prod_pubkey.edn
aws s3 cp s3://kixi-vault/prod_privkey.edn /root/prod_privkey.edn
