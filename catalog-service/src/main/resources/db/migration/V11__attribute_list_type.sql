-- LIST holds several values of one attribute (for example the sockets a cooler supports); stored as a JSON array of strings.
ALTER TABLE attribute_definitions DROP CONSTRAINT attribute_definitions_data_type_check;
ALTER TABLE attribute_definitions ADD CONSTRAINT attribute_definitions_data_type_check
    CHECK (data_type IN ('NUMBER', 'STRING', 'ENUM', 'BOOLEAN', 'LIST'));
