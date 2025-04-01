-- cleanup for the sake idempotent run
drop table if exists contact_history;
drop table if exists blocked_list;
drop table if exists client;
drop table if exists pos;
drop type if exists consent_enum;
drop type if exists client_type_enum;

create table pos
(
    id SERIAL PRIMARY KEY,
    location_lat NUMERIC(10, 6) NOT NULL,
    location_lon NUMERIC(10, 6) NOT NULL,
    open_hour TIME NOT NULL,
    close_hour TIME NOT NULL
);

create type consent_enum AS ENUM ('SMS', 'EMAIL', 'PUSH', 'SMS_EMAIL', 'EMAIL_PUSH', 'SMS_PUSH', 'SMS_EMAIL_PUSH');
create type client_type_enum AS ENUM ('INDIVIDUAL', 'BUSINESS');

create table client
(
    id SERIAL PRIMARY KEY,
    pos_id SERIAL REFERENCES pos(id) NOT NULL,
    msisdn CHAR(11),
    email VARCHAR(100),
    consents consent_enum,
    client_type client_type_enum NOT NULL
);

create table blocked_list(
    client_id INT PRIMARY KEY references client(id)
);

create table contact_history(
    id SERIAL PRIMARY KEY,
    client_id INT NOT NULL references client(id),
    event_time TIMESTAMP NOT NULL
);

---- POS
insert into pos(id, location_lat, location_lon, open_hour, close_hour) VALUES (1, 52.237049, 21.017532, '00:00:00', '23:59:59');
insert into pos(id, location_lat, location_lon, open_hour, close_hour) VALUES (2, 50.049683, 19.944544, '08:00:00', '15:00:00');
insert into pos(id, location_lat, location_lon, open_hour, close_hour) VALUES (3, 51.107883, 17.038538, '00:00:00', '23:59:59');

---- Clients
insert into client(id, pos_id, msisdn, email, consents, client_type) VALUES (1, 1, '48500500500', 'jan.kowalski@nussknacker.io', 'SMS', 'INDIVIDUAL');
insert into client(id, pos_id, msisdn, email, consents, client_type) VALUES (2, 1, '48500500501', 'zbigniew.paleta@nussknacker.io', 'SMS_EMAIL', 'BUSINESS');
insert into client(id, pos_id, msisdn, email, consents, client_type) VALUES (3, 1, '48500500502', 'genia.nowak@nussknacker.io', 'PUSH', 'INDIVIDUAL');
insert into client(id, pos_id, msisdn, email, consents, client_type) VALUES (4, 2, '48500500503', 'klaudia.wisniewska@nussknacker.io', 'SMS_EMAIL_PUSH', 'INDIVIDUAL');
insert into client(id, pos_id, msisdn, email, consents, client_type) VALUES (5, 2, '48500500504', 'teofil.benc@nussknacker.io', 'EMAIL', 'BUSINESS');
insert into client(id, pos_id, msisdn, email, consents, client_type) VALUES (6, 2, '48500500505', 'zdzislaw.lecina@nussknacker.io', null, 'BUSINESS');
insert into client(id, pos_id, msisdn, email, consents, client_type) VALUES (7, 2, '48500500506', 'ksenia.gorka@nussknacker.io', 'EMAIL_PUSH', 'INDIVIDUAL');
insert into client(id, pos_id, msisdn, email, consents, client_type) VALUES (8, 3, '48500500507', 'anna.milkowska@nussknacker.io', 'SMS_PUSH', 'BUSINESS');
insert into client(id, pos_id, msisdn, email, consents, client_type) VALUES (9, 3, '48500500508', 'john.doe@nussknacker.io', 'EMAIL', 'INDIVIDUAL');

---- Blocked
insert into blocked_list(client_id) values (5);

-- Contact history
insert into contact_history(client_id, event_time) VALUES (9, NOW() - INTERVAL '1 minutes');
