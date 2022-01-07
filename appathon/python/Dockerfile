FROM tiangolo/uwsgi-nginx-flask:python3.6-alpine3.7

WORKDIR /

COPY requirements.txt ./

RUN pip3 install -r /var/www/requirements.txt

RUN python3 main.py

