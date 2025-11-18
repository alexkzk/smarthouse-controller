FROM amazoncorretto:21

# Install git and ssh client so we can fetch and build from Git on each start
RUN yum update -y && \
    yum install -y git openssh-clients && \
    yum clean all

# Entry point script: handles SSH, git clone/fetch, build, run
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Working directory where repo will be cloned
WORKDIR /opt/smarthouse

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
