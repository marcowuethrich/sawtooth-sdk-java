# Copyright 2018 Bitwise IO
#
#
# ------------------------------------------------------------------------------

FROM maven:3-jdk-11

LABEL "install-type"="mounted"

EXPOSE 4004/tcp

RUN mkdir -p /project/sawtooth-sdk-java/ \
 && mkdir -p /var/log/sawtooth \
 && mkdir -p /var/lib/sawtooth \
 && mkdir -p /etc/sawtooth \
 && mkdir -p /etc/sawtooth/keys

ENV PATH=$PATH:/project/sawtooth-sdk-java/bin

WORKDIR /

RUN mkdir /build

COPY . /build/

RUN /build/bin/build_java_sdk

CMD /project/sawtooth-sdk-java/bin/build_java_archive
