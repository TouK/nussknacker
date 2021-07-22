import marshmallow as ma
from flask import Flask
from flask.views import MethodView
from flask_smorest import Api, Blueprint
import random

class Customer:
    def __init__(self, id, name, category):
        self.name = name
        self.id = id
        self.category = category

app = Flask(__name__, static_folder = '/static')

@app.route('/swagger')
def root():
    return app.send_static_file('swagger.json')

app.config['API_TITLE'] = 'Customers'
app.config['API_VERSION'] = 'v1'
app.config['OPENAPI_VERSION'] = '3.0.2'
api = Api(app)

class CustomerSchema(ma.Schema):
    id = ma.fields.Int(dump_only=True)
    name = ma.fields.String()
    category = ma.fields.String()

class CustomerQueryArgsSchema(ma.Schema):
    name = ma.fields.String()

blp = Blueprint(
    'customers', 'customers', url_prefix='/customers',
    description='Operations on customers'
)
@blp.route('/<customer_id>')
class CustomerById(MethodView):

    @blp.response(200, CustomerSchema)
    @blp.alt_response(404, 'NotFoundErrorResponse')
    @blp.alt_response(500, 'SomethingIsWrong')
    @blp.doc(operationId='getCustomer')
    def get(self, customer_id):
        #Well, python is not very reliable language :P
        if random.randrange(10) == 0:
            return "Unexpected failure!", 500
        idstr = str(customer_id)
        customers = {
            "1": Customer(customer_id, "John Doe", "STANDARD"),
            "2": Customer(customer_id, "Robert Wright", "GOLD"),
            "3": Customer(customer_id, "Юрий Шевчук", "PLATINUM"),
            "4": Customer(customer_id, "Иосиф Кобзон", "STANDARD")
        }
        if idstr in customers:
            return customers[idstr]
        else:
            return "Not found", 404

api.register_blueprint(blp)

