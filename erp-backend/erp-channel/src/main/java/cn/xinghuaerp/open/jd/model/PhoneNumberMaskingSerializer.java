package cn.xinghuaerp.open.jd.model;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;

// 自定义序列化器，将中间四位替换为星号
class PhoneNumberMaskingSerializer extends ValueSerializer<String> {
    @Override
    public void serialize(String phoneNumber, JsonGenerator jsonGenerator, SerializationContext serializerProvider) {
        if (phoneNumber != null && phoneNumber.length() >= 7) {
            // 将中间四位替换为星号
            String maskedPhoneNumber = phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(7);
            jsonGenerator.writeString(maskedPhoneNumber);
        } else {
            jsonGenerator.writeString(phoneNumber); // 如果长度不足，直接返回原值
        }
    }
}
