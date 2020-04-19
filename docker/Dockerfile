# Replace here with you jdk docker base image, such as for example "openjdk"
FROM adoptopenjdk/openjdk8

ENV JMETER_INSTALL /opt
ENV JMETER_MODE MASTER
ENV JMETER_BIN  ${JMETER_INSTALL}/apache-jmeter/bin
ENV PATH $PATH:$JMETER_BIN
ENV SLEEP 30

ADD resources/binaries/apache-jmeter.tgz ${JMETER_INSTALL}
RUN mv ${JMETER_INSTALL}/apache-jmeter-5.0 ${JMETER_INSTALL}/apache-jmeter
COPY resources/conf/user.properties  ${JMETER_BIN}

COPY resources/scripts/launch.sh ${JMETER_INSTALL}
RUN chmod 0755 ${JMETER_INSTALL}/launch.sh

RUN useradd -d /home/jmeter -u 1001 -s /bin/bash -d /home/jmeter jmeter

WORKDIR /home/jmeter
USER jmeter

EXPOSE 60000 1099 50000


ENTRYPOINT ["/opt/launch.sh"]


