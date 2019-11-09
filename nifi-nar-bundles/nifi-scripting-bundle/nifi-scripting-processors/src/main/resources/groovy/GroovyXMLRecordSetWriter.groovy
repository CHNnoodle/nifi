package groovy
import groovy.xml.MarkupBuilder

import org.apache.nifi.controller.AbstractControllerService
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.schema.access.SchemaNotFoundException
import org.apache.nifi.serialization.RecordSetWriter
import org.apache.nifi.serialization.RecordSetWriterFactory
import org.apache.nifi.serialization.WriteResult
import org.apache.nifi.serialization.record.Record
import org.apache.nifi.serialization.record.RecordSchema
import org.apache.nifi.serialization.record.RecordSet
import org.apache.nifi.stream.io.NonCloseableOutputStream
import org.apache.avro.Schema;
import org.apache.nifi.avro.AvroTypeUtil;


class GroovyRecordSetWriter implements RecordSetWriter {
    private int recordCount = 0;
    private final OutputStream out;

    public GroovyRecordSetWriter(final OutputStream out) {
        this.out = out;
    }

    @Override
    WriteResult write(Record r) throws IOException {
        new OutputStreamWriter(new NonCloseableOutputStream(out)).with {osw ->
            new MarkupBuilder(osw).record {
                r.schema.fieldNames.each {fieldName ->
                    "$fieldName" r.getValue(fieldName)
                }
            }
        }

        recordCount++;
        WriteResult.of(1, [:])
    }

    @Override
    String getMimeType() {
        return 'application/xml'
    }

    @Override
    WriteResult write(final RecordSet rs) throws IOException {
        int count = 0

        new OutputStreamWriter(new NonCloseableOutputStream(out)).with {osw ->
            def b = new MarkupBuilder(osw)
            b.getMkp().xmlDeclaration(encoding: 'UTF-8')
            b.recordSet {

                Record r
                while (r = rs.next()) {
                    count++

                    record {
                        rs.schema.fieldNames.each {fieldName ->
                            "$fieldName" r.getValue(fieldName)
                        }
                    }
                }
            }
        }
        WriteResult.of(count, [:])
    }

    public void beginRecordSet() throws IOException {
    }

    @Override
    public WriteResult finishRecordSet() throws IOException {
        return WriteResult.of(recordCount, [:]);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void flush() throws IOException {
    }
}

class GroovyRecordSetWriterFactory extends AbstractControllerService implements RecordSetWriterFactory {

    @Override
    RecordSchema getSchema(Map<String, String> variables, RecordSchema readSchema) throws SchemaNotFoundException, IOException {
        final String schemaText = variables.get("writer.schema")
        if (schemaText == null || schemaText.trim().isEmpty()) {
            return null
        }
        try {
            final Schema avroSchema = new Schema.Parser().parse(schemaText);
            return AvroTypeUtil.createSchema(avroSchema);
        } catch (final Exception e) {
            return null
        }

    }

    @Override
    RecordSetWriter createWriter(ComponentLog logger, RecordSchema schema, OutputStream out) throws SchemaNotFoundException, IOException {
        return new GroovyRecordSetWriter(out)
    }

}

writer = new GroovyRecordSetWriterFactory()