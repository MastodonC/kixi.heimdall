FROM zittix/docker-baseimage-java8
MAINTAINER Elise Huard <elise@mastodonc.com>

ENV SECRETS_BUCKET=$SECRETS_BUCKET
ENV AWS_REGION=$AWS_REGION

CMD ["/sbin/my_init"]

RUN apt-get update
RUN apt-get install -y \
software-properties-common \
python2.7 \
unzip

ADD deploy-jars/bcpkix-jdk15on-1.55.jar /usr/lib/jvm/java-8-oracle/jre/lib/ext/bcpkix-jdk15on-1.55.jar
ADD deploy-jars/bcprov-jdk15on-1.55.jar /usr/lib/jvm/java-8-oracle/jre/lib/ext/bcprov-jdk15on-1.55.jar
ADD deploy-jars/java.security /etc/java-8-oracle/security/java.security

RUN ln -s /usr/bin/python2.7 /usr/bin/python

ADD scripts/download-secrets.sh /root/download-secrets.sh

RUN mkdir /etc/service/auth

ADD target/kixi.heimdall.jar /srv/kixi.heimdall.jar

ADD scripts/run.sh /etc/service/auth/run

EXPOSE 3000
EXPOSE 5001

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
