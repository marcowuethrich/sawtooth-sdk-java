# Copyright 2018 Bitwise IO
#
# from the root dir run:
# docker build -t sawtooth-archive-tp-java:1.0 -f  examples/archive_java/Dockerfile .
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

COPY . /project/sawtooth-sdk-java/

CMD /project/sawtooth-sdk-java/bin/build_java_archive
