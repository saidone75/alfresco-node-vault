ARG GRAFANA_IMAGE
FROM ${GRAFANA_IMAGE}

WORKDIR /etc/grafana

COPY ./grafana/provisioning/ /etc/grafana/provisioning/
COPY ./grafana/dashboards/ /var/lib/grafana/dashboards/

USER root
RUN chown -R grafana /etc/grafana /var/lib/grafana
RUN chmod -R 755 /etc/grafana /var/lib/grafana
EXPOSE 3000

USER grafana
ENTRYPOINT ["grafana-server"]
CMD ["--homepath=/usr/share/grafana", "--config=/etc/grafana/grafana.ini"]