FROM phusion/baseimage:0.9.19
MAINTAINER Elise Huard <elise@mastodonc.com>

CMD ["/sbin/my_init"]

RUN apt-get install software-properties-common
RUN add-apt-repository -y ppa:webupd8team/java \
&& apt-get update \
&& echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections \
&& apt-get install -y \
software-properties-common \
oracle-java8-installer

RUN mkdir /etc/service/auth

ADD target/kixi.heimdall.jar /srv/kixi.heimdall.jar

ADD scripts/run.sh /etc/service/auth/run

ADD resources/prod_privkey.pem /root/prod_privkey.pem
ADD resources/prod_pubkey.pem /root/prod_pubkey.pem

EXPOSE 3000
EXPOSE 5001

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
