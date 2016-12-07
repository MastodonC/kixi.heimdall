FROM phusion/baseimage:0.9.19
MAINTAINER Elise Huard <elise@mastodonc.com>

ARG SECRETS_BUCKET
ARG AWS_REGION
ENV SECRETS_BUCKET=$SECRETS_BUCKET
ENV AWS_REGION=$AWS_REGION

CMD ["/sbin/my_init"]

RUN apt-get install software-properties-common
RUN add-apt-repository -y ppa:webupd8team/java \
&& apt-get update \
&& echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections \
&& apt-get install -y \
software-properties-common \
oracle-java8-installer \
oracle-java8-set-default

ADD deploy-jars/bcpkix-jdk15on-1.55.jar /usr/lib/jvm/java-8-oracle/jre/lib/ext/bcpkix-jdk15on-1.55.jar
ADD deploy-jars/bcprov-jdk15on-1.55.jar /usr/lib/jvm/java-8-oracle/jre/lib/ext/bcprov-jdk15on-1.55.jar
ADD deploy-jars/java.security /etc/java-8-oracle/security/java.security

RUN apt-get install -y python2.7 \
unzip

RUN ln -s /usr/bin/python2.7 /usr/bin/python

ADD scripts/download-secrets.sh /root/download-secrets.sh

RUN mkdir /etc/service/auth

ADD target/kixi.heimdall.jar /srv/kixi.heimdall.jar

ADD scripts/run.sh /etc/service/auth/run

EXPOSE 3000
EXPOSE 5001

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
