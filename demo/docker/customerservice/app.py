import marshmallow as ma
from flask import Flask
from flask.views import MethodView
from flask_smorest import Api, Blueprint

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
    @blp.doc(operationId='getCustomer')
    def get(self, customer_id):
        if customer_id == 10:
            return Customer(customer_id, "John Doe", "SILVER")
        else:
            return Customer(customer_id, "Robert Wright", "GOLD")

api.register_blueprint(blp)

