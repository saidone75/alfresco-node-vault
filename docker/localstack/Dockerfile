ARG LOCALSTACK_IMAGE
FROM ${LOCALSTACK_IMAGE}
COPY localstack-script.sh /etc/localstack/init/ready.d/script.sh
RUN chmod +x /etc/localstack/init/ready.d/script.sh
