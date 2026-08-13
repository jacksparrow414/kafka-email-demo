package com.message.common.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

/**
 * Avro SpecificRecord 与 JSON 字符串互转工具.
 * 用于失败消息落库(message_failed表)时的序列化, 以及定时重试时从库中读出后的反序列化.
 * 注意: Avro的JSON编码中union类型(如可空字段)会被包装为{"string": "value"}形式, 与Jackson的输出格式不同,
 * 因此message_failed.message_content_json_format中的内容只能用本工具类读写
 *
 * @author jacksparrow414
 */
public final class AvroJsonUtil {

    private AvroJsonUtil() {
    }

    /**
     * 将Avro SpecificRecord序列化为JSON字符串
     */
    public static <T extends SpecificRecord> String toJson(T record) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JsonEncoder encoder = EncoderFactory.get().jsonEncoder(record.getSchema(), out);
            new SpecificDatumWriter<T>(record.getSchema()).write(record, encoder);
            encoder.flush();
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to serialize avro record to json", e);
        }
    }

    /**
     * 将JSON字符串反序列化为指定的Avro SpecificRecord
     */
    public static <T extends SpecificRecord> T fromJson(String json, Class<T> type) {
        try {
            SpecificDatumReader<T> reader = new SpecificDatumReader<>(type);
            JsonDecoder decoder = DecoderFactory.get().jsonDecoder(reader.getSchema(), json);
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to deserialize json to avro record: " + type.getName(), e);
        }
    }
}
