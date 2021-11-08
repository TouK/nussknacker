FROM nginx

COPY configs/nginx/* /etc/nginx/conf.d/

WORKDIR /usr/share/nginx/html

COPY dist/ .

COPY env.sh .
COPY .env .
RUN chmod +x env.sh

CMD ["/bin/sh", "-c", "./env.sh && nginx -g \"daemon off;\""]
