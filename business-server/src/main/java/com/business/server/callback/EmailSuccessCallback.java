package com.business.server.callback;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

/**
 * 邮件发送成功之后的回调示例类.
 * 实例会被Jackson序列化为JSON字符串, 作为{@link com.message.common.dto.CallbackMetaData#instanceJsonStr}的一部分,
 * 由回调消费者反序列化之后通过反射调用{@link #onSuccess()}
 *
 * @author jacksparrow414
 */
@Log
@Getter
@Setter
public class EmailSuccessCallback {

    private String messageId;

    private String userName;

    public void onSuccess() {
        log.info("email sent success callback invoked, messageId: " + messageId + ", userName: " + userName);
        // 实际的业务逻辑, 例如更新数据库中邮件发送状态等
    }
}
