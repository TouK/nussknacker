FROM python:3
RUN pip install flask-smorest
EXPOSE 5000
ADD app.py /
RUN mkdir /static && flask openapi print > /static/swagger.json
CMD [ "python3", "-m" , "flask", "run", "--host=0.0.0.0"]

